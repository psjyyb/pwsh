package com.pwsh.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.pwsh.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 도메인 이벤트 배선 검증.
 *
 * <p>확인하려는 것은 "이벤트가 발행됐다"가 아니라 <b>이벤트로 갈아탄 뒤에도 결과가 같고,
 * 반응처가 여러 개면 한 번의 발행으로 모두 동작하는가</b>다.
 * <ul>
 *   <li>로그인 1회 발행 → 활동로그 기록 + 접속 세션 열기 두 리스너가 모두 동작</li>
 *   <li>토큰 무효화 창구(MemberService.invalidateToken)를 타는 진입점은
 *       세션 종료가 자동으로 따라온다 — 호출부가 세션 닫기를 잊을 수 없다</li>
 * </ul>
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class DomainEventTest extends IntegrationTest {

    private String openSessionId(String memberId) {
        List<String> ids = jdbc.queryForList(
                "SELECT login_session_id::text FROM login_session WHERE member_id = ? AND end_dt IS NULL",
                String.class, memberId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Test
    void one_login_event_drives_both_listeners() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = "evtlogin";
        assertEquals(200, createMember(admin, id).statusCode());

        int logBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_log WHERE event_cd = 'LOGIN' AND member_id = ?", Integer.class, id);

        accessToken(id, "Test1234!@");

        // 발행은 한 번인데 반응은 둘 — 활동로그 1건 증가 + 열린 세션 1건
        int logAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_log WHERE event_cd = 'LOGIN' AND member_id = ?", Integer.class, id);
        assertEquals(logBefore + 1, logAfter, "LOGIN 활동로그 리스너가 동작해야 한다");
        assertNotNull(openSessionId(id), "접속 세션 리스너가 동작해야 한다");

        cleanup(id);
    }

    @Test
    void token_invalidation_funnel_always_closes_session() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = "evtforce";
        assertEquals(200, createMember(admin, id).statusCode());
        String token = accessToken(id, "Test1234!@");
        assertNotNull(openSessionId(id));

        // 회원관리의 강제 로그아웃은 세션 도메인을 직접 부르지 않는다.
        // 창구(invalidateToken)가 이벤트를 발행하고 리스너가 세션을 닫는다.
        assertEquals(200, post("/api/adm/member/updateMemberForceLogout.do",
                "{\"memberId\":\"" + id + "\"}", admin).statusCode());

        assertNull(openSessionId(id), "창구를 타면 세션 종료가 자동으로 따라와야 한다");
        assertEquals("FORCE", jdbc.queryForObject(
                "SELECT end_reason FROM login_session WHERE member_id = ? ORDER BY login_session_id DESC LIMIT 1",
                String.class, id));
        // 창구는 token_ver도 올린다 — 인증 필요 경로가 401
        assertEquals(401, post("/api/auth/pwExtend", "{}", token).statusCode());

        cleanup(id);
    }

    /**
     * 이 프로젝트에만 있는 진입점(탈퇴·계정정지)도 창구를 탄다.
     * CMS 틀에는 없는 경로라, 이벤트로 바꾸면서 빠뜨리기 쉬운 자리다.
     */
    @Test
    void pwsh_only_entry_points_go_through_the_funnel() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 탈퇴 → WITHDRAW
        String out = "evtout";
        assertEquals(200, createMember(admin, out).statusCode());
        String outToken = accessToken(out, "Test1234!@");
        assertNotNull(openSessionId(out));
        assertEquals(200, post("/api/auth/withdraw", "{\"currentPw\":\"Test1234!@\"}", outToken).statusCode());
        assertNull(openSessionId(out));
        assertEquals("WITHDRAW", lastEndReason(out));

        // 관리자 정지 → SUSPEND
        String susp = "evtsusp";
        assertEquals(200, createMember(admin, susp).statusCode());
        String suspToken = accessToken(susp, "Test1234!@");
        assertNotNull(openSessionId(susp));
        assertEquals(200, post("/api/adm/member/updateMemberStatus.do",
                "{\"memberId\":\"" + susp + "\",\"statusCd\":\"STATUS03\"}", admin).statusCode());
        assertNull(openSessionId(susp));
        assertEquals("SUSPEND", lastEndReason(susp));
        assertEquals(401, post("/api/auth/pwExtend", "{}", suspToken).statusCode());

        cleanup(out);
        cleanup(susp);
    }

    private String lastEndReason(String memberId) {
        return jdbc.queryForObject(
                "SELECT end_reason FROM login_session WHERE member_id = ? ORDER BY login_session_id DESC LIMIT 1",
                String.class, memberId);
    }

    private void cleanup(String memberId) {
        jdbc.update("DELETE FROM login_session WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM event_log WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM auth_member WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
    }
}
