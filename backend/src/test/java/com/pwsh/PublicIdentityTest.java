package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 공개 식별자(handle) 계약 회귀 방지.
 * 사용자에게 내려가는 응답에는 <b>로그인 ID(reg_id/user_id)와 pgcrypto 키(encKey)가 없어야</b> 하고,
 * 회원 지목은 handle로만 이뤄져야 한다. 매퍼에 컬럼을 하나 추가하는 것만으로 조용히 깨질 수 있는
 * 계약이라 실제 HTTP 응답 본문을 문자열째로 검사한다.
 */
class PublicIdentityTest extends IntegrationTest {

    /** 응답 본문에 특정 JSON 키가 없어야 함(문자열 검사 — 필드가 새로 섞여 들어오면 바로 실패). */
    private void assertNoKey(String body, String key, String where) {
        assertFalse(body.contains("\"" + key + "\""), where + " 응답에 " + key + "가 노출됨: " + body);
    }

    @Test
    void public_responses_expose_handle_not_login_id() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"식별자취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        String boardId = JsonPath.read(post("/api/adm/hobby/selectHobbyView.do",
                "{\"rowId\":\"" + hobbyId + "\"}", null).body(), "$.data.bbsinfoId");

        assertEquals(200, signup("ida", "식별자A", "ida@test.local").statusCode());
        assertEquals(200, signup("idb", "식별자B", "idb@test.local").statusCode());
        String ta = accessToken("ida", "Test1234!@");
        String tb = accessToken("idb", "Test1234!@");

        // 내 handle 확인 — me()는 handle을 주고 userId는 주지 않는다
        String meBody = post("/api/auth/me", "{}", ta).body();
        String handleA = JsonPath.read(meBody, "$.data.handle");
        assertNotNull(handleA);
        assertFalse(handleA.equals("idA") || handleA.equals("ida"), "handle이 로그인 ID와 같으면 익명성이 없다");
        assertNoKey(meBody, "encKey", "me");

        // 게시글 — 목록/상세 모두 regHandle만
        String bbsId = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + boardId + "\",\"title\":\"식별자 글\",\"context\":\"x\"}", ta).body(), "$.data");
        String listBody = post("/api/adm/bbs/selectBbsList.do",
                "{\"bbsinfoId\":\"" + boardId + "\",\"pageIndex\":1,\"size\":10}", null).body();
        assertTrue(listBody.contains("\"regHandle\""), "게시글 목록에 regHandle이 있어야 한다");
        assertNoKey(listBody, "regId", "게시글 목록");
        assertNoKey(listBody, "encKey", "게시글 목록");

        String viewBody = post("/api/adm/bbs/selectBbsView.do",
                "{\"rowId\":\"" + bbsId + "\",\"viewUp\":\"N\"}", tb).body();
        assertNoKey(viewBody, "regId", "게시글 상세");
        assertEquals("N", JsonPath.read(viewBody, "$.data.mineYn"), "남의 글은 mineYn=N (서버 계산)");

        // 댓글
        assertEquals(200, post("/api/adm/comment/insertComment.do",
                "{\"bbsId\":\"" + bbsId + "\",\"context\":\"댓글\"}", tb).statusCode());
        String cmtBody = post("/api/adm/comment/selectCommentList.do",
                "{\"bbsId\":\"" + bbsId + "\"}", ta).body();
        assertTrue(cmtBody.contains("\"regHandle\""));
        assertNoKey(cmtBody, "regId", "댓글 목록");

        // 모집 — 목록/상세
        String future = LocalDate.now().plusDays(7).toString();
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"식별자 모집\",\"capacity\":\"3\","
                        + "\"meetDt\":\"" + future + "\"}", ta).body(), "$.data");
        String rListBody = post("/api/adm/recruit/selectRecruitList.do", "{\"pageIndex\":1,\"size\":10}", null).body();
        assertTrue(rListBody.contains("\"regHandle\""));
        assertNoKey(rListBody, "regId", "모집 목록");
        assertNoKey(rListBody, "encKey", "모집 목록");
        String rViewBody = post("/api/adm/recruit/selectRecruitView.do",
                "{\"rowId\":\"" + rid + "\",\"viewUp\":\"N\"}", tb).body();
        assertNoKey(rViewBody, "regId", "모집 상세");

        // 공개 프로필 — handle로 조회하고, 응답에 로그인 ID가 없어야 한다
        String profBody = post("/api/auth/userProfile", "{\"handle\":\"" + handleA + "\"}", tb).body();
        assertEquals("식별자A", JsonPath.read(profBody, "$.data.nickname"));
        assertNoKey(profBody, "userId", "공개 프로필");
        assertNoKey(profBody, "encKey", "공개 프로필");

        // 쪽지 — 상대 지목도 handle, 스레드에 발신/수신 ID 없음
        assertEquals(200, post("/api/adm/message/insertMessage.do",
                "{\"receiverHandle\":\"" + handleA + "\",\"content\":\"안녕\"}", tb).statusCode());
        String convBody = post("/api/adm/message/selectMessageList.do", "{}", ta).body();
        assertTrue(convBody.contains("\"otherHandle\""));
        String threadBody = post("/api/adm/message/selectMessageListThread.do",
                "{\"otherHandle\":\"" + JsonPath.read(post("/api/auth/me", "{}", tb).body(), "$.data.handle") + "\"}",
                ta).body();
        assertNoKey(threadBody, "senderId", "쪽지 스레드");
        assertNoKey(threadBody, "receiverId", "쪽지 스레드");

        // 후기 — 참여자가 주최자에게(모임 종료 후). 목록에 대상 로그인 ID가 없어야 한다
        String applyOk = applyAndAccept(rid, ta, tb, "idb");
        assertEquals("APPLY02", applyOk);
        assertEquals(200, post("/api/adm/recruit/updateRecruitStatus.do",
                "{\"rowId\":\"" + rid + "\",\"statusCd\":\"RECRUIT02\"}", ta).statusCode());
        assertEquals(200, post("/api/adm/review/insertReview.do",
                "{\"recruitId\":\"" + rid + "\",\"targetHandle\":\"" + handleA + "\",\"rating\":\"5\",\"content\":\"좋았어요\"}",
                tb).statusCode());
        String revBody = post("/api/adm/review/selectReviewList.do", "{\"targetHandle\":\"" + handleA + "\"}", null).body();
        assertTrue(revBody.contains("\"regHandle\""));
        assertNoKey(revBody, "targetId", "후기 목록");
        assertNoKey(revBody, "regId", "후기 목록");

        // 차단 목록도 handle 기준
        assertEquals(200, post("/api/adm/block/updateBlockToggle.do",
                "{\"blockedHandle\":\"" + handleA + "\"}", tb).statusCode());
        String blockBody = post("/api/adm/block/selectBlockList.do", "{}", tb).body();
        assertTrue(blockBody.contains("\"blockedHandle\""));
        assertNoKey(blockBody, "blockedId", "차단 목록");

        // 존재하지 않는 handle은 404 — 순차 ID 열거가 불가능해야 한다
        assertEquals(404, post("/api/auth/userProfile", "{\"handle\":\"zzzznotexist\"}", tb).statusCode());
    }

    /** 신청 후 주최자가 수락 → 최종 상태 반환. */
    private String applyAndAccept(String recruitId, String hostTok, String applicantTok, String applicantId)
            throws Exception {
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + recruitId + "\"}", applicantTok).statusCode());
        String applyId = jdbc.queryForObject(
                "SELECT apply_id::text FROM t_recruit_apply WHERE recruit_id = ?::integer AND user_id = ?",
                String.class, recruitId, applicantId);
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + applyId + "\",\"applyStatus\":\"APPLY02\"}", hostTok).statusCode());
        return jdbc.queryForObject("SELECT apply_status FROM t_recruit_apply WHERE apply_id = ?::integer",
                String.class, applyId);
    }
}
