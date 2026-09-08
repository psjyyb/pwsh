package com.pwsh.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 접속 세션 기록·강제종료.
 * - 로그인하면 열린 세션이 생기고 목록에 '접속중'으로 뜨는지
 * - 로그아웃/재로그인/비번변경이 각각의 종료 사유로 닫히는지
 * - 관리자 강제종료가 세션을 닫고 토큰까지 무효화하는지(401)
 * - 세션 만료(마지막 활동이 오래됨)가 접속중에서 빠지는지
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class LoginSessionTest extends IntegrationTest {

    /** 해당 사용자의 열린 세션 1건(없으면 null). */
    private String openSessionId(String memberId) {
        List<String> ids = jdbc.queryForList(
                "SELECT login_session_id::text FROM login_session WHERE member_id = ? AND end_dt IS NULL",
                String.class, memberId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String lastEndReason(String memberId) {
        return jdbc.queryForObject(
                "SELECT end_reason FROM login_session WHERE member_id = ? ORDER BY login_session_id DESC LIMIT 1",
                String.class, memberId);
    }

    @Test
    void login_opens_session_and_logout_closes_it() throws Exception {
        String user = accessToken("user", "user1234!");
        String sessionId = openSessionId("user");
        assertNotNull(sessionId, "로그인하면 열린 세션이 생겨야 한다");

        // 관리자 목록에 '접속중'으로 보인다
        String admin = accessToken("admin", "admin1234!");
        String body = post("/api/adm/loginsession/selectLoginSessionList.do",
                "{\"filterStatus\":\"ACTIVE\",\"filterKeyword\":\"user\"}", admin).body();
        assertTrue(((Integer) JsonPath.read(body, "$.data.totalCount")) >= 1);
        assertEquals("Y", JsonPath.read(body, "$.data.list[0].activeYn"));
        assertEquals("user", JsonPath.read(body, "$.data.list[0].memberId"));

        // 로그아웃 → 세션 종료(LOGOUT)
        assertEquals(200, post("/api/auth/logout", "{}", user).statusCode());
        assertNull(openSessionId("user"), "로그아웃하면 열린 세션이 없어야 한다");
        assertEquals("LOGOUT", lastEndReason("user"));

        jdbc.update("DELETE FROM login_session WHERE member_id = 'user'");
    }

    @Test
    void relogin_closes_previous_session() throws Exception {
        accessToken("user", "user1234!");
        String first = openSessionId("user");
        assertNotNull(first);

        // 다른 기기 로그인(단일세션 last-wins) → 앞 세션은 RELOGIN으로 닫히고 새 세션이 열린다
        accessToken("user", "user1234!");
        String second = openSessionId("user");
        assertNotNull(second);
        assertTrue(!first.equals(second), "새 세션이 열려야 한다");
        assertEquals("RELOGIN", jdbc.queryForObject(
                "SELECT end_reason FROM login_session WHERE login_session_id = ?::integer", String.class, first));

        jdbc.update("DELETE FROM login_session WHERE member_id = 'user'");
    }

    @Test
    void admin_force_end_closes_session_and_invalidates_token() throws Exception {
        String user = accessToken("user", "user1234!");
        String admin = accessToken("admin", "admin1234!");
        String sessionId = openSessionId("user");
        assertNotNull(sessionId);

        // 강제종료 전에는 토큰이 살아 있다.
        // 판정에는 반드시 '인증이 필요한' 엔드포인트를 쓴다 — selectPostList 같은 permitAll 경로는
        // 토큰이 무효여도 익명으로 통과(200)해서 무효화 여부를 구분하지 못한다.
        assertEquals(200, post("/api/auth/pwExtend", "{}", user).statusCode());

        assertEquals(200, post("/api/adm/loginsession/updateLoginSessionForceEnd.do",
                "{\"rowId\":\"" + sessionId + "\"}", admin).statusCode());

        assertNull(openSessionId("user"), "강제종료하면 세션이 닫혀야 한다");
        assertEquals("FORCE", lastEndReason("user"));
        // 세션만 닫고 토큰을 살려두면 강제종료가 무의미하다 → token_ver 증가로 401
        assertEquals(401, post("/api/auth/pwExtend", "{}", user).statusCode());

        // 없는 세션은 404
        assertEquals(404, post("/api/adm/loginsession/updateLoginSessionForceEnd.do",
                "{\"rowId\":\"99999999\"}", admin).statusCode());

        jdbc.update("DELETE FROM login_session WHERE member_id = 'user'");
    }

    @Test
    void password_change_closes_session() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertEquals(200, createMember(admin, "sesspw").statusCode());
        accessToken("sesspw", "Test1234!@");
        assertNotNull(openSessionId("sesspw"));

        // 관리자 비밀번호 리셋 → 대상 세션 종료
        assertEquals(200, post("/api/adm/member/updateMemberPassword.do",
                "{\"rowId\":\"sesspw\",\"password\":\"Reset1234!@\"}", admin).statusCode());
        assertNull(openSessionId("sesspw"));
        assertEquals("PWCHANGE", lastEndReason("sesspw"));

        jdbc.update("DELETE FROM login_session WHERE member_id = 'sesspw'");
        jdbc.update("DELETE FROM auth_member WHERE member_id = 'sesspw'");
        jdbc.update("DELETE FROM member WHERE member_id = 'sesspw'");
    }

    /** 이 서비스에만 있는 종료 사유 — 셀프 탈퇴는 WITHDRAW, 관리자 정지는 SUSPEND로 닫힌다. */
    @Test
    void withdraw_and_suspend_close_session() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 탈퇴 → WITHDRAW
        assertEquals(200, createMember(admin, "sessout").statusCode());
        String out = accessToken("sessout", "Test1234!@");
        assertNotNull(openSessionId("sessout"));
        assertEquals(200, post("/api/auth/withdraw", "{\"currentPw\":\"Test1234!@\"}", out).statusCode());
        assertNull(openSessionId("sessout"));
        assertEquals("WITHDRAW", lastEndReason("sessout"));

        // 관리자 정지 → SUSPEND
        assertEquals(200, createMember(admin, "sesssusp").statusCode());
        accessToken("sesssusp", "Test1234!@");
        assertNotNull(openSessionId("sesssusp"));
        assertEquals(200, post("/api/adm/member/updateMemberStatus.do",
                "{\"memberId\":\"sesssusp\",\"statusCd\":\"STATUS03\"}", admin).statusCode());
        assertNull(openSessionId("sesssusp"));
        assertEquals("SUSPEND", lastEndReason("sesssusp"));

        for (String id : new String[] {"sessout", "sesssusp"}) {
            jdbc.update("DELETE FROM login_session WHERE member_id = ?", id);
            jdbc.update("DELETE FROM auth_member WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member WHERE member_id = ?", id);
        }
    }

    @Test
    void idle_session_drops_out_of_active_list() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 검색은 member_id 부분일치라 'user' 같은 흔한 조각을 쓰면 다른 테스트가 만든 세션까지 잡힌다
        // → 이 테스트 전용 계정을 만들어 그 아이디로만 조회한다.
        String id = "sessidle";
        assertEquals(200, createMember(admin, id).statusCode());
        accessToken(id, "Test1234!@");
        String sessionId = openSessionId(id);
        assertNotNull(sessionId);

        // 마지막 활동을 설정된 세션 만료(분)보다 훨씬 앞으로 밀면 '접속중'에서 빠진다.
        // (열린 행을 닫아주는 배치 없이 조회 시점에 판정한다 — 설정 변경이 곧바로 반영되게)
        jdbc.update("UPDATE login_session SET last_seen_dt = NOW() - INTERVAL '10 days' WHERE login_session_id = ?::integer",
                Integer.valueOf(sessionId));

        String active = post("/api/adm/loginsession/selectLoginSessionList.do",
                "{\"filterStatus\":\"ACTIVE\",\"filterKeyword\":\"" + id + "\"}", admin).body();
        assertEquals(0, (int) (Integer) JsonPath.read(active, "$.data.totalCount"));

        String ended = post("/api/adm/loginsession/selectLoginSessionList.do",
                "{\"filterStatus\":\"ENDED\",\"filterKeyword\":\"" + id + "\"}", admin).body();
        assertEquals(1, (int) (Integer) JsonPath.read(ended, "$.data.totalCount"));

        jdbc.update("DELETE FROM login_session WHERE member_id = ?", id);
        jdbc.update("DELETE FROM auth_member WHERE member_id = ?", id);
        jdbc.update("DELETE FROM member WHERE member_id = ?", id);
    }
}
