package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.accessip.service.AccessIpService;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 로그인 이력 — 성공·실패를 모두 남긴다.
 *
 * <p>조용히 틀릴 수 있는 곳 — 실패는 예외로 끝나서, 기록 코드를 성공 경로에만 두면 정작 봐야 할
 * 실패(계정 추측·무차별 대입)가 하나도 안 남는다. 그리고 실패를 기록하는 코드가 응답을 바꾸면
 * (401 → 500 등) 로그인 화면의 안내가 깨진다. 그래서 결과 코드와 응답 상태를 함께 확인한다.
 */
class LoginLogTest extends IntegrationTest {

    private static final String PW = "Test1234!@";

    @Autowired
    private AccessIpService accessIpService;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM login_log WHERE login_id LIKE 'zzll%'");
        jdbc.update("DELETE FROM auth_member WHERE member_id LIKE 'zzll%'");
        jdbc.update("DELETE FROM member WHERE member_id LIKE 'zzll%'");
        jdbc.update("DELETE FROM access_ip");
        jdbc.update("UPDATE config SET acc_ip_yn = 'N'");
        accessIpService.evict();
    }

    @Test
    @DisplayName("성공한 로그인은 결과·IP·브라우저 정보와 함께 남는다")
    void successIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzll1");

        assertThat(login("zzll1", PW).statusCode()).isEqualTo(200);

        Map<String, Object> row = lastRow("zzll1");
        assertThat(row.get("result_cd")).isEqualTo("LOGIN01");
        assertThat(row.get("reg_ip")).as("어디서 들어왔는지가 남아야 한다").isNotNull();
        assertThat(row.get("user_agent")).isNotNull();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 실패로 남고, 응답은 기존 그대로 401이다")
    void wrongPasswordIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzll2");

        assertThat(login("zzll2", "Wrong1234!@").statusCode()).isEqualTo(401);

        Map<String, Object> row = lastRow("zzll2");
        assertThat(row.get("result_cd")).isEqualTo("LOGIN02");
        assertThat((String) row.get("fail_message")).as("몇 번째 실패인지까지 남아야 추적이 된다").contains("1/");
    }

    @Test
    @DisplayName("없는 아이디로 시도해도 입력한 아이디 그대로 남는다(계정 추측 공격 분석용)")
    void unknownIdIsLogged() throws Exception {
        assertThat(login("zzll-nobody", PW).statusCode()).isEqualTo(401);

        assertThat(lastRow("zzll-nobody").get("result_cd")).isEqualTo("LOGIN03");
    }

    @Test
    @DisplayName("잠긴·정지된·휴면 계정의 시도는 각각의 사유로 남는다")
    void blockedAccountsAreLoggedByReason() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzll3");
        createMember(admin, "zzll4");
        createMember(admin, "zzll5");
        jdbc.update("UPDATE member SET status_cd = 'STATUS02', lock_until_dt = NOW() WHERE member_id = 'zzll3'");
        jdbc.update("UPDATE member SET status_cd = 'STATUS03' WHERE member_id = 'zzll4'");
        jdbc.update("UPDATE member SET status_cd = 'STATUS04' WHERE member_id = 'zzll5'");

        // 비밀번호가 맞아도 막힌다 — 응답 코드는 기존 그대로
        assertThat(login("zzll3", PW).statusCode()).isEqualTo(401);
        assertThat(login("zzll4", PW).statusCode()).isEqualTo(401);
        assertThat(login("zzll5", PW).statusCode()).isEqualTo(401);

        assertThat(lastRow("zzll3").get("result_cd")).isEqualTo("LOGIN04");
        assertThat(lastRow("zzll4").get("result_cd")).isEqualTo("LOGIN05");
        assertThat(lastRow("zzll5").get("result_cd")).isEqualTo("LOGIN06");
    }

    @Test
    @DisplayName("이번 실패로 잠기면 사유는 비밀번호 불일치다(이미 잠겨 있던 것과 구분)")
    void lockingFailureIsPasswordMismatch() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzll6");
        Integer limit = jdbc.queryForObject("SELECT fail_cnt_limit FROM config", Integer.class);
        jdbc.update("UPDATE member SET fail_cnt = ? WHERE member_id = 'zzll6'", limit - 1);

        login("zzll6", "Wrong1234!@");

        Map<String, Object> row = lastRow("zzll6");
        assertThat(row.get("result_cd")).isEqualTo("LOGIN02");
        assertThat((String) row.get("fail_message")).contains("잠겼");
    }

    @Test
    @DisplayName("허용되지 않은 IP에서의 관리자 로그인은 IP 차단으로 남는다")
    void disallowedAdminIpIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/member/insertMember.do",
                "{\"memberId\":\"zzll7\",\"memberName\":\"관리\",\"typeCd\":\"MEM02\","
                        + "\"statusCd\":\"STATUS01\",\"password\":\"" + PW + "\"}", admin).statusCode()).isEqualTo(200);
        jdbc.update("INSERT INTO access_ip (ip, description, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) "
                + "VALUES ('203.0.113.1', '테스트', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')");
        jdbc.update("UPDATE config SET acc_ip_yn = 'Y'");
        accessIpService.evict();

        assertThat(login("zzll7", PW).statusCode()).isEqualTo(403);

        assertThat(lastRow("zzll7").get("result_cd")).isEqualTo("LOGIN07");
    }

    @Test
    @DisplayName("아주 긴 아이디도 잘라서 남기고 로그인 응답은 그대로다(기록 때문에 500이 나면 안 된다)")
    void longIdIsTruncated() throws Exception {
        String longId = "zzll" + "x".repeat(300);

        assertThat(login(longId, PW).statusCode()).isEqualTo(401);

        String stored = jdbc.queryForObject(
                "SELECT login_id FROM login_log WHERE login_id LIKE 'zzllxxx%' ORDER BY login_log_id DESC LIMIT 1",
                String.class);
        assertThat(stored).hasSize(50);
    }

    @Test
    @DisplayName("관리자는 결과·아이디로 걸러 볼 수 있고, 일반 회원은 볼 수 없다")
    void listIsAdminOnlyAndFilterable() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzll8");
        login("zzll8", "Wrong1234!@");
        login("zzll8", PW);

        String body = post("/api/adm/loginlog/selectLoginLogList.do",
                "{\"resultCd\":\"LOGIN02\",\"filterField\":\"login_id\",\"filterKeyword\":\"zzll8\"}", admin).body();
        List<String> results = JsonPath.read(body, "$.data.list[*].resultCd");
        assertThat(results).containsExactly("LOGIN02");
        assertThat((String) JsonPath.read(body, "$.data.list[0].resultName")).as("코드명이 함께 나가야 화면이 읽힌다")
                .isNotBlank();

        // 다른 사람의 로그인 기록(아이디·IP)은 관리 정보다
        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/loginlog/selectLoginLogList.do", "{}", user).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("검색어의 % 는 글자 그대로 찾는다 — 건수와 목록이 같은 결과여야 한다")
    void percentInKeywordIsLiteral() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzll9");
        login("zzll9", PW);
        login("zzll50%off", PW); // '%'가 실제로 든 (없는) 아이디

        String body = post("/api/adm/loginlog/selectLoginLogList.do",
                "{\"filterField\":\"login_id\",\"filterKeyword\":\"zzll50%\"}", admin).body();
        // 이스케이프가 빠지면 zzll9까지 걸리고, 같은 VO를 두 번 이스케이프하면 건수는 1인데 목록은 빈다
        assertThat((Integer) JsonPath.read(body, "$.data.totalCount")).isEqualTo(1);
        List<String> ids = JsonPath.read(body, "$.data.list[*].loginId");
        assertThat(ids).containsExactly("zzll50%off");
    }

    // ===== helpers =====

    private Map<String, Object> lastRow(String loginId) {
        return jdbc.queryForMap("SELECT result_cd, fail_message, reg_ip, user_agent FROM login_log"
                + " WHERE login_id = ? ORDER BY login_log_id DESC LIMIT 1", loginId);
    }
}
