package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 회원 차단 + 내 취미 피드 검증.
 * - 차단: 쪽지 차단 + 차단한 회원의 글/댓글/모집이 내 목록에서 사라지고 <b>총건수도 함께</b> 줄어야 한다
 *   (목록과 totCnt가 어긋나면 페이징이 깨진다)
 * - 피드: 담은 취미 것만, 비밀글·답글·차단 회원 글은 제외
 */
class BlockAndFeedTest extends IntegrationTest {

    @Test
    void block_hides_content_consistently_and_feed_respects_my_hobbies() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 취미 2개(담을 것 / 담지 않을 것)와 각각의 게시판
        String hobbyIn = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"피드담은취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        String boardIn = JsonPath.read(post("/api/adm/hobby/selectHobbyView.do",
                "{\"rowId\":\"" + hobbyIn + "\"}", null).body(), "$.data.bbsinfoId");
        String hobbyOut = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"피드안담은취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        String boardOut = JsonPath.read(post("/api/adm/hobby/selectHobbyView.do",
                "{\"rowId\":\"" + hobbyOut + "\"}", null).body(), "$.data.bbsinfoId");

        assertEquals(200, signup("blkme", "차단하는사람", "blkme@test.local").statusCode());
        assertEquals(200, signup("blkyou", "차단당하는사람", "blkyou@test.local").statusCode());
        String me = accessToken("blkme", "Test1234!@");
        String you = accessToken("blkyou", "Test1234!@");
        String myHandle = JsonPath.read(post("/api/auth/me", "{}", me).body(), "$.data.handle");
        String yourHandle = JsonPath.read(post("/api/auth/me", "{}", you).body(), "$.data.handle");

        // ===== 피드: 비로그인 차단 =====
        assertEquals(401, post("/api/adm/feed/selectFeedList.do", "{\"pageIndex\":1,\"size\":20}", null).statusCode());

        // 담은 취미 1개
        assertEquals(200, post("/api/adm/userHobby/insertUserHobby.do",
                "{\"hobbyId\":\"" + hobbyIn + "\"}", me).statusCode());

        // 담은 취미 게시판 글 / 담지 않은 취미 게시판 글
        String postIn = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + boardIn + "\",\"title\":\"담은취미 글\",\"context\":\"x\"}", you).body(), "$.data");
        String postOut = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + boardOut + "\",\"title\":\"안담은취미 글\",\"context\":\"x\"}", you).body(), "$.data");
        // 비밀글과 답글은 피드에서 빠져야 한다
        String secret = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + boardIn + "\",\"title\":\"비밀글\",\"context\":\"x\",\"secretYn\":\"Y\",\"bbsPw\":\"pw12\"}",
                you).body(), "$.data");
        String reply = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + boardIn + "\",\"title\":\"답글\",\"context\":\"x\",\"pBbsId\":\"" + postIn + "\"}",
                you).body(), "$.data");
        assertNotNull(reply);

        String feed = post("/api/adm/feed/selectFeedList.do", "{\"pageIndex\":1,\"size\":50}", me).body();
        assertTrue(feedHas(feed, "BBS", postIn), "담은 취미 글은 피드에 있어야 한다");
        assertFalse(feedHas(feed, "BBS", postOut), "담지 않은 취미 글은 피드에 없어야 한다");
        assertFalse(feedHas(feed, "BBS", secret), "비밀글은 피드에서 제외");
        assertFalse(feedHas(feed, "BBS", reply), "답글은 피드에서 제외(원글 기준)");
        assertEquals(1, (int) (Integer) JsonPath.read(feed, "$.data.myHobbyCnt"), "담은 취미 수를 함께 준다");

        // 모집도 피드에 — 담은 취미의 모집만
        String future = LocalDate.now().plusDays(9).toString();
        String recIn = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyIn + "\",\"title\":\"담은취미 모집\",\"capacity\":\"3\",\"meetDt\":\"" + future + "\"}",
                you).body(), "$.data");
        String recOut = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyOut + "\",\"title\":\"안담은취미 모집\",\"capacity\":\"3\",\"meetDt\":\"" + future + "\"}",
                you).body(), "$.data");
        String feed2 = post("/api/adm/feed/selectFeedList.do", "{\"pageIndex\":1,\"size\":50}", me).body();
        assertTrue(feedHas(feed2, "RECRUIT", recIn));
        assertFalse(feedHas(feed2, "RECRUIT", recOut));

        // 필터: BBS/RECRUIT 합이 전체와 같아야 한다
        int all = JsonPath.read(feed2, "$.data.totCnt");
        int onlyBbs = JsonPath.read(post("/api/adm/feed/selectFeedList.do",
                "{\"feedFilter\":\"BBS\",\"pageIndex\":1,\"size\":50}", me).body(), "$.data.totCnt");
        int onlyRec = JsonPath.read(post("/api/adm/feed/selectFeedList.do",
                "{\"feedFilter\":\"RECRUIT\",\"pageIndex\":1,\"size\":50}", me).body(), "$.data.totCnt");
        assertEquals(all, onlyBbs + onlyRec, "필터 합 = 전체");
        assertNotEquals(200, post("/api/adm/feed/selectFeedList.do",
                "{\"feedFilter\":\"HACK\",\"pageIndex\":1,\"size\":20}", me).statusCode(), "잘못된 구분은 거부");

        // ===== 차단 =====
        assertEquals(200, post("/api/adm/comment/insertComment.do",
                "{\"bbsId\":\"" + postIn + "\",\"context\":\"차단될 댓글\"}", you).statusCode());

        int totBefore = JsonPath.read(post("/api/adm/bbs/selectBbsList.do",
                "{\"bbsinfoId\":\"" + boardIn + "\",\"pageIndex\":1,\"size\":50}", me).body(), "$.data.totCnt");

        assertEquals(200, post("/api/adm/block/updateBlockToggle.do",
                "{\"blockedHandle\":\"" + yourHandle + "\"}", me).statusCode());

        // 쪽지 차단(차단한 사람에게는 보낼 수 없다)
        assertEquals(403, post("/api/adm/message/insertMessage.do",
                "{\"receiverHandle\":\"" + myHandle + "\",\"content\":\"막혀야 함\"}", you).statusCode());

        // 글: 목록에서 사라지고 totCnt도 함께 줄어든다(목록/카운트 불일치는 페이징을 깨뜨린다)
        String afterBlock = post("/api/adm/bbs/selectBbsList.do",
                "{\"bbsinfoId\":\"" + boardIn + "\",\"pageIndex\":1,\"size\":50}", me).body();
        int totAfter = JsonPath.read(afterBlock, "$.data.totCnt");
        List<Object> ids = JsonPath.read(afterBlock, "$.data.list[?(@.rowId=='" + postIn + "')]");
        assertEquals(0, ids.size(), "차단 회원의 글이 내 목록에 남아 있다");
        assertTrue(totAfter < totBefore, "totCnt도 함께 줄어야 한다 (" + totBefore + " -> " + totAfter + ")");
        assertEquals(totAfter, ((List<Object>) JsonPath.read(afterBlock, "$.data.list")).size(),
                "목록 건수와 totCnt가 일치해야 한다");

        // 댓글도 숨는다
        String cmts = post("/api/adm/comment/selectCommentList.do", "{\"bbsId\":\"" + postIn + "\"}", me).body();
        assertFalse(cmts.contains("차단될 댓글"), "차단 회원의 댓글이 보인다");

        // 모집 목록에서도 제외
        String recList = post("/api/adm/recruit/selectRecruitList.do", "{\"pageIndex\":1,\"size\":50}", me).body();
        assertEquals(0, ((List<Object>) JsonPath.read(recList, "$.data.list[?(@.rowId=='" + recIn + "')]")).size(),
                "차단 회원의 모집이 보인다");

        // 피드에서도 제외
        String feedBlocked = post("/api/adm/feed/selectFeedList.do", "{\"pageIndex\":1,\"size\":50}", me).body();
        assertFalse(feedHas(feedBlocked, "BBS", postIn), "차단 후 피드에 글이 남아 있다");
        assertFalse(feedHas(feedBlocked, "RECRUIT", recIn), "차단 후 피드에 모집이 남아 있다");

        // 다른 사람(비로그인)에게는 그대로 보인다 — 차단은 내 화면에만 적용
        String guestList = post("/api/adm/bbs/selectBbsList.do",
                "{\"bbsinfoId\":\"" + boardIn + "\",\"pageIndex\":1,\"size\":50}", null).body();
        assertEquals(1, ((List<Object>) JsonPath.read(guestList, "$.data.list[?(@.rowId=='" + postIn + "')]")).size(),
                "차단은 나에게만 적용돼야 한다");

        // 해제하면 복원
        assertEquals(200, post("/api/adm/block/updateBlockToggle.do",
                "{\"blockedHandle\":\"" + yourHandle + "\"}", me).statusCode());
        String restored = post("/api/adm/bbs/selectBbsList.do",
                "{\"bbsinfoId\":\"" + boardIn + "\",\"pageIndex\":1,\"size\":50}", me).body();
        assertEquals(1, ((List<Object>) JsonPath.read(restored, "$.data.list[?(@.rowId=='" + postIn + "')]")).size(),
                "차단 해제 후 복원되지 않았다");
        assertEquals(totBefore, (int) (Integer) JsonPath.read(restored, "$.data.totCnt"));
    }

    /** 피드 응답에 해당 유형·PK 항목이 있는지. */
    private boolean feedHas(String body, String feedType, String rowId) {
        List<Object> hit = JsonPath.read(body,
                "$.data.list[?(@.feedType=='" + feedType + "' && @.rowId=='" + rowId + "')]");
        return !hit.isEmpty();
    }
}
