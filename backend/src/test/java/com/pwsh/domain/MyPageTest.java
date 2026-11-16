package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 마이페이지 보강(CMS 1.2.37) — 약관 동의 내역 · 관리자 셀프 탈퇴 차단 · 탈퇴는 관리자 삭제와 같은 길 · 닉네임 공백·길이.
 *
 * <p>내 글·모집·북마크 등 이 서비스 고유 항목은 각 도메인 테스트가 본다.
 * <p>조용히 틀릴 수 있는 곳 — 비밀번호 없이(또는 틀려도) 탈퇴되는 것, 관리자가 실수로 스스로 탈퇴하는 것, 탈퇴했는데 권한그룹 매핑이
 * 남는 것, 들고 있던 토큰으로 계속 쓰이는 것, 그리고 내 정보를 본 것만으로 개인정보 접근기록이 쌓이는 것.
 */
class MyPageTest extends IntegrationTest {

    private static final String PW = "Test1234!@";

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM privacy_log");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM member_policy WHERE policy_id IN (SELECT policy_id FROM policy WHERE title LIKE 'ZZ마이%')");
        jdbc.update("DELETE FROM policy WHERE title LIKE 'ZZ마이%'");
        jdbc.update("DELETE FROM privacy_log");
    }

    @Test
    @DisplayName("비로그인은 내 정보·닉네임 변경·탈퇴를 쓸 수 없다")
    void guestBlocked() throws Exception {
        assertThat(post("/api/auth/me", "{}", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/auth/nickname", "{\"nickname\":\"zz게스트\"}", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/auth/withdraw", "{\"currentPw\":\"x\"}", null).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("내 정보에 약관 동의 내역(지금 안 쓰는 약관도)과 관리자 여부가 실린다 — 본인 조회는 개인정보 접근기록에 남지 않는다")
    void meShowsAgreements() throws Exception {
        assertThat(signup("zzmy01", "zz마이01", "zzmy01@test.local").statusCode()).isEqualTo(200); // 필수 약관에 동의하며 가입
        String token = accessToken("zzmy01", PW);
        jdbc.update("INSERT INTO policy (title, content, type_cd, req_yn, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES ('ZZ마이 옛 약관', '<p>본문</p>', 'POLICY03', 'N', 90, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')");
        jdbc.update("INSERT INTO member_policy (member_id, policy_id, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " SELECT 'zzmy01', policy_id, 'Y', 'zzmy01', 'zzmy01', NOW(), NOW(), '127.0.0.1', '127.0.0.1' FROM policy WHERE title = 'ZZ마이 옛 약관'");
        jdbc.update("UPDATE policy SET use_yn = 'N' WHERE title = 'ZZ마이 옛 약관'");

        String body = post("/api/auth/me", "{}", token).body();
        assertThat((String) JsonPath.read(body, "$.data.adminYn")).isEqualTo("N");
        List<Map<String, Object>> agreed = JsonPath.read(body, "$.data.policies");
        int required = jdbc.queryForObject("SELECT COUNT(*) FROM policy WHERE use_yn = 'Y' AND req_yn = 'Y'", Integer.class);
        assertThat(agreed).as("가입 때 동의한 필수 약관 + 내려간 옛 약관").hasSize(required + 1);
        assertThat(agreed).allMatch(p -> p.get("agreeDt") != null);
        assertThat(agreed.stream().filter(p -> "N".equals(p.get("useYn"))).map(p -> p.get("title")))
                .as("바뀌어 내려간 약관도 내가 동의했던 이력이라 보여 준다").containsExactly("ZZ마이 옛 약관");
        assertThat(body).doesNotContain("password").doesNotContain("tokenVer");

        Thread.sleep(500); // 접근기록은 응답 뒤에 적재된다 — 늦게 들어오는 기록을 놓치지 않게 기다렸다 센다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM privacy_log WHERE request_uri = '/api/auth/me'", Integer.class))
                .as("본인 정보만 본 요청은 접근기록이 아니다").isZero();
    }

    @Test
    @DisplayName("닉네임 변경: 앞뒤 공백은 지우고, 남이 쓰는 닉네임(409)·빈 값·30자 초과는 거절된다(지금 내 닉네임 그대로는 통과)")
    void changeNickname() throws Exception {
        assertThat(signup("zzmy02", "zz마이02", "zzmy02@test.local").statusCode()).isEqualTo(200);
        String token = accessToken("zzmy02", PW);
        assertThat(post("/api/auth/nickname", "{\"nickname\":\" zz마이02-새 \"}", token).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT nickname FROM member WHERE member_id = 'zzmy02'", String.class))
                .as("앞뒤 공백은 지운다").isEqualTo("zz마이02-새");
        assertThat(post("/api/auth/nickname", "{\"nickname\":\"zz마이02-새\"}", token).statusCode()).as("지금 내 닉네임 그대로").isEqualTo(200);

        assertThat(post("/api/auth/nickname", "{\"nickname\":\"관리자\"}", token).statusCode()).as("관리자(admin)의 닉네임").isEqualTo(409);
        assertThat(post("/api/auth/nickname", "{\"nickname\":\"  \"}", token).statusCode()).isEqualTo(400);
        assertThat(post("/api/auth/nickname", "{\"nickname\":\"" + "가".repeat(31) + "\"}", token).statusCode())
                .as("DB 길이(30)를 넘으면 500이 아니라 400").isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT nickname FROM member WHERE member_id = 'zzmy02'", String.class)).isEqualTo("zz마이02-새");
    }

    @Test
    @DisplayName("탈퇴: 비밀번호가 맞아야 되고, 되면 계정이 닫히고 탈퇴 시각이 찍히며 권한그룹 매핑이 정리되고 들고 있던 토큰도 끊긴다")
    void withdraw() throws Exception {
        assertThat(signup("zzmy03", "zz마이03", "zzmy03@test.local").statusCode()).isEqualTo(200);
        String token = accessToken("zzmy03", PW);

        assertThat(post("/api/auth/withdraw", "{\"currentPw\":\"Wrong1234!@\"}", token).statusCode()).isEqualTo(400);
        assertThat(post("/api/auth/withdraw", "{}", token).statusCode()).as("비밀번호 없이").isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT use_yn FROM member WHERE member_id = 'zzmy03'", String.class))
                .as("틀리면 아무것도 안 바뀐다").isEqualTo("Y");

        assertThat(post("/api/auth/withdraw", "{\"currentPw\":\"" + PW + "\"}", token).statusCode()).isEqualTo(200);
        Map<String, Object> m = jdbc.queryForMap("SELECT use_yn, withdraw_dt FROM member WHERE member_id = 'zzmy03'");
        assertThat(m.get("use_yn")).isEqualTo("N");
        assertThat(m.get("withdraw_dt")).as("없으면 개인정보가 영원히 파기되지 않는다").isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_member WHERE member_id = 'zzmy03'", Integer.class))
                .as("관리자 삭제와 같은 길 — 권한그룹 매핑도 정리").isZero();
        assertThat(post("/api/auth/me", "{}", token).statusCode()).as("들고 있던 토큰").isEqualTo(401);
        assertThat(login("zzmy03", PW).statusCode()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("관리자는 스스로 탈퇴할 수 없다(비밀번호가 맞아도) — 관리자 삭제는 다른 관리자가 회원관리에서")
    void adminCannotWithdraw() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/auth/withdraw", "{\"currentPw\":\"admin1234!\"}", admin).statusCode()).isEqualTo(403);
        assertThat((String) JsonPath.read(post("/api/auth/me", "{}", admin).body(), "$.data.adminYn")).isEqualTo("Y");
        assertThat(jdbc.queryForObject("SELECT use_yn FROM member WHERE member_id = 'admin'", String.class)).isEqualTo("Y");
    }
}
