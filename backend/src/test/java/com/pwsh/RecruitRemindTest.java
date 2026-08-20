package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.domain.recruit.service.RecruitService;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 모임 리마인더(D-1) 배치 검증. 스케줄 트리거는 시간 기반이라 배치 본체(sendMeetReminders)를
 * 실제 빈으로 호출하고, 모집·신청은 HTTP 경로로 실제 생성한다. 모킹 0.
 */
class RecruitRemindTest extends IntegrationTest {

    @Autowired
    private RecruitService recruitService;

    @Test
    void reminder_notifies_host_and_accepted_members_only_once() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 취미(전용 게시판 자동 생성) → 모집 주최자/신청자/거절자 계정
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"리마인드취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);
        assertEquals(200, signup("rmhost", "리마인드주최", "rmhost@test.local").statusCode());
        assertEquals(200, signup("rmok", "수락자", "rmok@test.local").statusCode());
        assertEquals(200, signup("rmno", "대기자", "rmno@test.local").statusCode());
        String hostTok = accessToken("rmhost", "Test1234!@");
        String okTok = accessToken("rmok", "Test1234!@");
        String noTok = accessToken("rmno", "Test1234!@");

        String tomorrow = LocalDate.now().plusDays(1).toString();

        // 내일 모임 1건 + 이틀 뒤 모임 1건(대상 아님)
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"내일모임\",\"capacity\":\"5\","
                        + "\"meetDt\":\"" + tomorrow + "\"}", hostTok).body(), "$.data");
        String later = LocalDate.now().plusDays(2).toString();
        String ridLater = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"모레모임\",\"capacity\":\"5\","
                        + "\"meetDt\":\"" + later + "\"}", hostTok).body(), "$.data");

        // 신청 2건 → 1건만 수락(APPLY02), 1건은 대기(APPLY01)
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", okTok).statusCode());
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", noTok).statusCode());
        String okApplyId = jdbc.queryForObject(
                "SELECT apply_id::text FROM recruit_apply WHERE recruit_id = ?::integer AND member_id = 'rmok'",
                String.class, rid);
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + okApplyId + "\",\"applyCd\":\"APPLY02\"}", hostTok).statusCode());

        // 신청/수락 알림은 이미 적재되므로 리마인더만 세어 비교한다
        int sent = recruitService.sendMeetReminders(tomorrow);
        assertEquals(2, sent, "주최자 + 수락 참여자 2명에게만 발송");

        assertEquals(1, remindCnt("rmhost", rid), "주최자에게 1건");
        assertEquals(1, remindCnt("rmok", rid), "수락 참여자에게 1건");
        assertEquals(0, remindCnt("rmno", rid), "대기 신청자에겐 발송하지 않음");

        // 링크·문구 확인(클릭 시 모집 상세로)
        String link = jdbc.queryForObject(
                "SELECT link_url FROM notification WHERE member_id = 'rmhost' AND type = 'REMIND'", String.class);
        assertEquals("/gen/recruit/" + rid, link);
        String content = jdbc.queryForObject(
                "SELECT content FROM notification WHERE member_id = 'rmok' AND type = 'REMIND'", String.class);
        assertTrue(content.contains("내일모임") && content.contains(tomorrow), "모임명·일자 포함: " + content);

        // 같은 날 재실행해도 중복 발송되지 않는다(재기동/중복 스케줄 안전)
        assertEquals(0, recruitService.sendMeetReminders(tomorrow), "재실행 시 0건");
        assertEquals(1, remindCnt("rmhost", rid));

        // 모레 모임은 오늘 대상이 아니다
        assertEquals(0, remindCnt("rmhost", ridLater));

        // 마감(RECRUIT02) 모임도 대상 — 확정된 모임이라 리마인더가 필요
        assertEquals(200, post("/api/adm/recruit/updateRecruitStatus.do",
                "{\"rowId\":\"" + ridLater + "\",\"statusCd\":\"RECRUIT02\"}", hostTok).statusCode());
        assertEquals(1, recruitService.sendMeetReminders(later), "마감 모임도 발송(주최자 1명)");
        assertEquals(1, remindCnt("rmhost", ridLater));

        // 삭제된 모집은 대상에서 제외
        assertEquals(200, post("/api/adm/recruit/deleteRecruit.do",
                "{\"rowId\":\"" + rid + "\"}", hostTok).statusCode());
        jdbc.update("DELETE FROM notification WHERE type = 'REMIND'");
        assertEquals(0, recruitService.sendMeetReminders(tomorrow), "삭제된 모집은 발송하지 않음");
    }

    /** 특정 회원이 특정 모집으로 받은 리마인더 건수. */
    private int remindCnt(String memberId, String recruitId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE member_id = ? AND type = 'REMIND' AND link_url = ?",
                Integer.class, memberId, "/gen/recruit/" + recruitId);
        return n == null ? 0 : n;
    }
}
