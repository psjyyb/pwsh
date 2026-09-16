package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 개인정보 접근 로그 — <b>자동 적재</b>가 실제로 되는지, 그리고 <b>불필요한 것이 안 쌓이는지</b> 검증.
 *
 * <p>이 기능은 개발자가 호출하는 코드가 없다(복호화 SQL 탐지 → 요청 종료 시 적재). 그래서 배선이
 * 끊겨도 컴파일도 되고 화면도 멀쩡하다 — 빈 목록을 보고 "조회한 사람이 없구나"라고 오해하게 된다.
 * 반대로 걸러내기가 깨지면 요청 수만큼 기록이 쌓여 정작 봐야 할 조회가 묻힌다. 양쪽을 다 고정한다.
 */
class PrivacyLogTest extends IntegrationTest {

    @BeforeEach
    void clearLogs() throws Exception {
        // 적재는 응답이 나간 뒤(afterCompletion)라 앞 테스트의 기록이 늦게 떨어질 수 있다.
        // 지우기 전에 잠깐 기다리지 않으면 그게 다음 테스트 결과로 새어 들어온다.
        Thread.sleep(300);
        jdbc.update("DELETE FROM privacy_log");
    }

    @Test
    @DisplayName("회원 목록을 보면 접근기록이 남는다 — 조회자·대상·건수·수행업무까지")
    void memberListIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String body = post("/api/adm/member/selectMemberList.do", "{\"pageNo\":1,\"pageSize\":10}", admin).body();
        // 목록에 누가 나오는지는 앞선 테스트가 만든 계정에 따라 달라진다 → 실제 응답에서 대상을 가져온다
        List<String> shown = JsonPath.read(body, "$.data.list[*].memberId");
        assertThat(shown).isNotEmpty();

        Map<String, Object> row = awaitOneLog();
        assertThat(row.get("member_id")).isEqualTo("admin");
        assertThat((String) row.get("request_uri")).isEqualTo("/api/adm/member/selectMemberList.do");
        assertThat((String) row.get("sql_ids")).contains("memberDAO.selectList");
        assertThat((String) row.get("target_ids")).contains(shown.get(0));
        assertThat((Integer) row.get("access_cnt")).isPositive();
    }

    @Test
    @DisplayName("회원 상세 조회도 남고, 대상은 그 회원 한 명이다")
    void memberViewIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        post("/api/adm/member/selectMemberView.do", "{\"rowId\":\"user\"}", admin);

        Map<String, Object> row = awaitOneLog();
        assertThat((String) row.get("request_uri")).isEqualTo("/api/adm/member/selectMemberView.do");
        assertThat((String) row.get("target_ids")).isEqualTo("user");
        assertThat((Integer) row.get("access_cnt")).isEqualTo(1);
    }

    @Test
    @DisplayName("이름으로 검색하면 조건절 안에서만 복호화하는 조회까지 잡힌다 — 여기를 놓치면 검색 이력이 빈다")
    void conditionalDecryptIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        post("/api/adm/member/selectMemberList.do",
                "{\"filterKeyword\":\"테스트\",\"pageNo\":1,\"pageSize\":10}", admin);

        Map<String, Object> row = awaitOneLog();
        // selectListTotalCount는 filterKeyword가 있을 때만 DECRYPT를 쓴다(동적 SQL).
        // 구문 단위로 판정을 캐시하면 이 줄이 빠진다.
        assertThat((String) row.get("sql_ids")).contains("memberDAO.selectListTotalCount");
    }

    @Test
    @DisplayName("본인 정보만 읽는 일반 요청은 남기지 않는다 — 남기면 요청 수만큼 기록이 쌓인다")
    void ownInfoOnlyIsNotLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 인증 필터가 매 요청 자기 계정을 복호화해 읽는다. 개인정보를 다루지 않는 화면을 몇 번 불러도
        // 접근기록은 비어 있어야 한다.
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/adm/code/selectCodeList.do", "{}", admin).statusCode()).isEqualTo(200);
        }
        assertThat(sleepThenCount()).isZero();
    }

    @Test
    @DisplayName("비로그인 요청은 남기지 않는다(로그인 인증은 event_log가 남긴다)")
    void guestRequestIsNotLogged() throws Exception {
        assertThat(login("admin", "admin1234!").statusCode()).isEqualTo(200);
        assertThat(login("admin", "틀린비번").statusCode()).isNotEqualTo(200);
        assertThat(sleepThenCount()).isZero();
    }

    @Test
    @DisplayName("접근기록 화면 자체는 기록을 만들지 않는다 — 자기 자신을 기록하는 고리 방지")
    void privacyLogScreenDoesNotLogItself() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/adm/privacylog/selectPrivacylogList.do", "{\"pageNo\":1,\"pageSize\":10}", admin)
                    .statusCode()).isEqualTo(200);
        }
        assertThat(sleepThenCount()).isZero();
    }

    @Test
    @DisplayName("접근기록은 목록·상세만 있고 등록·수정·삭제 API는 없다")
    void logIsAppendOnly() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/privacylog/insertPrivacylog.do", "{}", admin).statusCode()).isEqualTo(404);
        assertThat(post("/api/adm/privacylog/updatePrivacylog.do", "{}", admin).statusCode()).isEqualTo(404);
        assertThat(post("/api/adm/privacylog/deletePrivacylog.do", "{\"rowId\":\"1\"}", admin).statusCode())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("관리자 화면에서 목록·상세를 읽을 수 있고, 비로그인은 막힌다")
    void adminCanReadLogs() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        post("/api/adm/member/selectMemberView.do", "{\"rowId\":\"user\"}", admin);
        awaitOneLog();

        assertThat(post("/api/adm/privacylog/selectPrivacylogList.do", "{\"pageNo\":1,\"pageSize\":10}", admin)
                .statusCode()).isEqualTo(200);
        assertThat(post("/api/adm/privacylog/selectPrivacylogList.do", "{}", null).statusCode()).isEqualTo(401);
    }

    // ===== helpers =====

    /**
     * 적재는 응답이 나간 뒤(afterCompletion) 일어나므로 곧바로 조회하면 아직 없을 수 있다.
     * 잠깐 기다렸다가 1건을 가져온다(없으면 실패).
     */
    private Map<String, Object> awaitOneLog() throws Exception {
        for (int i = 0; i < 40; i++) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT member_id, request_uri, sql_ids, target_ids, access_cnt"
                            + " FROM privacy_log ORDER BY privacy_log_id DESC LIMIT 1");
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("개인정보 접근기록이 남지 않았다(자동 적재 배선 확인 필요)");
    }

    /** "안 남아야 한다"는 기다려도 안 생기는 것을 봐야 한다. */
    private int sleepThenCount() throws Exception {
        Thread.sleep(500);
        return jdbc.queryForObject("SELECT COUNT(*) FROM privacy_log", Integer.class);
    }
}
