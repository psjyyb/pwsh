package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * 관리자 조치 감사 로그 검증 — 신고 처리·회원 제재·강제 로그아웃·권한 변경이
 * event_log에 '누가/언제/무엇에/어떤 조치'로 남는지. (EventLogAspect가 못 잡는 {variant} 변형 지점)
 */
class AdminAuditTest extends IntegrationTest {

    @Test
    void admin_actions_are_audited_with_specific_event_types() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertEquals(200, signup("audituser", "감사대상", "audituser@test.local").statusCode());
        String victim = accessToken("audituser", "Test1234!@");

        // 신고 대상 글 준비(취미 게시판)
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"감사취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        String boardId = JsonPath.read(post("/api/adm/hobby/selectHobbyView.do",
                "{\"rowId\":\"" + hobbyId + "\"}", null).body(), "$.data.boardId");
        String postId = JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + boardId + "\",\"title\":\"신고될 글\",\"content\":\"x\"}", victim).body(), "$.data");
        assertNotNull(postId);
        assertEquals(200, post("/api/adm/report/insertReport.do",
                "{\"targetType\":\"POST\",\"targetId\":\"" + postId + "\",\"reason\":\"감사테스트\",\"reasonCd\":\"REPORT01\"}",
                victim).statusCode());
        String reportId = jdbc.queryForObject(
                "SELECT report_id::text FROM report WHERE reason = '감사테스트'", String.class);

        // 1) 삭제조치 → REPORT_RESOLVE
        assertEquals(200, post("/api/adm/report/updateReportStatus.do",
                "{\"rowId\":\"" + reportId + "\",\"statusType\":\"RESOLVED\"}", admin).statusCode());
        assertEquals(1, auditCnt("REPORT_RESOLVE", "report", reportId));
        assertEquals("admin", auditActor("REPORT_RESOLVE", reportId), "조치자가 기록된다");

        // 2) 되돌리기 → REPORT_REOPEN
        assertEquals(200, post("/api/adm/report/updateReportStatus.do",
                "{\"rowId\":\"" + reportId + "\",\"statusType\":\"PENDING\"}", admin).statusCode());
        assertEquals(1, auditCnt("REPORT_REOPEN", "report", reportId));

        // 3) 반려 → REPORT_DISMISS
        assertEquals(200, post("/api/adm/report/updateReportStatus.do",
                "{\"rowId\":\"" + reportId + "\",\"statusType\":\"DISMISSED\"}", admin).statusCode());
        assertEquals(1, auditCnt("REPORT_DISMISS", "report", reportId));

        // 4) 회원 정지 → MEMBER_SUSPEND, 해제 → MEMBER_RESTORE
        assertEquals(200, post("/api/adm/member/updateMemberStatus.do",
                "{\"memberId\":\"audituser\",\"statusCd\":\"STATUS03\"}", admin).statusCode());
        assertEquals(1, auditCnt("MEMBER_SUSPEND", "member", "audituser"));
        assertEquals(200, post("/api/adm/member/updateMemberStatus.do",
                "{\"memberId\":\"audituser\",\"statusCd\":\"STATUS01\"}", admin).statusCode());
        assertEquals(1, auditCnt("MEMBER_RESTORE", "member", "audituser"));

        // 5) 강제 로그아웃 → MEMBER_LOGOUT
        assertEquals(200, post("/api/adm/member/updateMemberForceLogout.do",
                "{\"memberId\":\"audituser\"}", admin).statusCode());
        assertEquals(1, auditCnt("MEMBER_LOGOUT", "member", "audituser"));

        // 6) 권한그룹 변경 → MEMBER_AUTH_GROUP
        assertEquals(200, post("/api/adm/member/updateMemberAuthGroup.do",
                "{\"memberId\":\"audituser\",\"authGroupIds\":[\"MEMBER\"]}", admin).statusCode());
        assertEquals(1, auditCnt("MEMBER_AUTH_GROUP", "member", "audituser"));

        // 감사 유형은 모두 code(EVENT00)에 등록돼 있어야 화면에서 한글로 보인다
        for (String t : new String[] {"REPORT_RESOLVE", "REPORT_DISMISS", "REPORT_REOPEN",
                "MEMBER_SUSPEND", "MEMBER_RESTORE", "MEMBER_LOGOUT", "MEMBER_AUTH_GROUP"}) {
            Integer c = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM code WHERE p_code_id = 'EVENT00' AND code_id = ? AND use_yn = 'Y'",
                    Integer.class, t);
            assertEquals(1, c, t + " 코드 누락");
        }

        // 관리자 활동로그 화면에서 조회되는지(실제 API 경로)
        String logs = post("/api/adm/eventlog/selectEventlogList.do",
                "{\"pageNo\":1,\"pageSize\":50}", admin).body();
        assertTrue(logs.contains("MEMBER_SUSPEND"), "활동로그 목록에 조치가 노출된다");

        // 일상 행위(알림 읽음)는 감사 로그를 남기지 않는다 — 잡음 방지
        // 앞의 정지·강제로그아웃으로 기존 토큰이 무효화됐으므로 재로그인해서 확인한다.
        victim = accessToken("audituser", "Test1234!@");
        int before = auditTotal();
        assertEquals(200, post("/api/adm/notification/updateNotificationReadAll.do", "{}", victim).statusCode());
        assertEquals(before, auditTotal(), "알림 읽음 같은 일상 행위는 기록하지 않는다");
    }

    @Test
    void force_logout_and_auth_group_change_record_actor_and_show_names() throws Exception {
        // CMS 1.2.28에서 CMS도 같은 기록을 남기게 됐다 — 그때 CMS 테스트가 본 조치자·화면 이름을 여기서도 확인한다
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzaudit01");
        assertEquals(200, post("/api/adm/member/updateMemberForceLogout.do",
                "{\"memberId\":\"zzaudit01\"}", admin).statusCode());
        assertEquals(200, post("/api/adm/member/updateMemberAuthGroup.do",
                "{\"memberId\":\"zzaudit01\",\"authGroupIds\":[\"MEMBER\"]}", admin).statusCode());

        assertEquals("admin", auditActor("MEMBER_LOGOUT", "zzaudit01"), "조치자가 기록된다");
        assertEquals("admin", auditActor("MEMBER_AUTH_GROUP", "zzaudit01"), "조치자가 기록된다");
        // 코드(EVENT00)가 없으면 행은 남아도 화면에는 MEMBER_LOGOUT 같은 코드값이 그대로 보인다
        assertEquals("강제 로그아웃", eventName(admin, "MEMBER_LOGOUT"));
        assertEquals("권한그룹 변경", eventName(admin, "MEMBER_AUTH_GROUP"));
    }

    private String eventName(String admin, String eventCd) throws Exception {
        String list = post("/api/adm/eventlog/selectEventlogList.do", "{\"eventCd\":\"" + eventCd + "\"}", admin).body();
        return JsonPath.read(list, "$.data.list[0].eventName");
    }

    private int auditCnt(String eventCd, String table, String targetId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_log WHERE event_cd = ? AND target_table = ? AND target_id = ?",
                Integer.class, eventCd, table, targetId);
        return n == null ? 0 : n;
    }

    private String auditActor(String eventCd, String targetId) {
        return jdbc.queryForObject(
                "SELECT member_id FROM event_log WHERE event_cd = ? AND target_id = ? ORDER BY event_log_id DESC LIMIT 1",
                String.class, eventCd, targetId);
    }

    private int auditTotal() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM event_log", Integer.class);
        return n == null ? 0 : n;
    }
}
