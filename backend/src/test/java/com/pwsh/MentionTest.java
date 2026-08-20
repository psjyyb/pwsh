package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * @닉네임 멘션 알림 검증.
 * - 댓글에서 언급하면 그 회원에게 MENTION 알림이 가고, 글쓴이 알림과 중복되지 않는지
 * - 없는 닉네임·본인 언급은 아무 일도 없는지
 * - 모임 단체 대화의 멘션은 <b>대화 멤버에게만</b> 가는지(바깥 사람을 부르지 못한다)
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class MentionTest extends IntegrationTest {

    @Test
    void comment_mention_notifies_target_without_duplicate() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"멘션취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        String boardId = jdbc.queryForObject(
                "SELECT board_id::text FROM hobby WHERE hobby_id = ?::integer", String.class, hobbyId);

        assertEquals(200, signup("mnwriter", "글쓴이", "mnwriter@test.local").statusCode());
        assertEquals(200, signup("mnreader", "언급대상", "mnreader@test.local").statusCode());
        String writer = accessToken("mnwriter", "Test1234!@");
        String reader = accessToken("mnreader", "Test1234!@");

        String postId = JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + boardId + "\",\"title\":\"멘션 테스트 글\",\"context\":\"본문\"}",
                writer).body(), "$.data");

        int before = notificationCnt("mnreader");
        assertEquals(200, post("/api/adm/comment/insertComment.do",
                "{\"postId\":\"" + postId + "\",\"context\":\"@언급대상 이거 같이 가실래요?\"}", reader).statusCode());
        assertEquals(before + 0, notificationCnt("mnreader"), "본인을 언급하면 알림 없음");

        // 글쓴이가 언급대상을 부르면 → MENTION 알림 1건
        int r0 = notificationCnt("mnreader");
        assertEquals(200, post("/api/adm/comment/insertComment.do",
                "{\"postId\":\"" + postId + "\",\"context\":\"@언급대상 님 의견 궁금해요\"}", writer).statusCode());
        assertEquals(r0 + 1, notificationCnt("mnreader"), "언급된 회원에게 알림 1건");
        assertEquals("MENTION", lastNotiType("mnreader"));

        // 언급대상이 글쓴이를 언급 → 글쓴이는 '댓글 알림'과 '멘션 알림'을 중복으로 받지 않는다
        int w0 = notificationCnt("mnwriter");
        assertEquals(200, post("/api/adm/comment/insertComment.do",
                "{\"postId\":\"" + postId + "\",\"context\":\"@글쓴이 확인 부탁드려요\"}", reader).statusCode());
        assertEquals(w0 + 1, notificationCnt("mnwriter"), "멘션+댓글이 겹쳐도 1건만");
        assertEquals("MENTION", lastNotiType("mnwriter"));

        // 없는 닉네임은 무시(오류 없이 등록만 된다)
        int w1 = notificationCnt("mnwriter");
        assertEquals(200, post("/api/adm/comment/insertComment.do",
                "{\"postId\":\"" + postId + "\",\"context\":\"@존재하지않는닉네임 안녕\"}", reader).statusCode());
        assertEquals(w1 + 1, notificationCnt("mnwriter"), "없는 닉네임은 무시되고 글쓴이 댓글 알림만");

        post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + postId + "\"}", writer);
    }

    @Test
    void chat_mention_only_reaches_members() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"멘션대화취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);

        assertEquals(200, signup("mchost", "대화주최자", "mchost@test.local").statusCode());
        assertEquals(200, signup("mcmem", "대화멤버", "mcmem@test.local").statusCode());
        assertEquals(200, signup("mcout", "바깥사람", "mcout@test.local").statusCode());
        String host = accessToken("mchost", "Test1234!@");
        String mem = accessToken("mcmem", "Test1234!@");

        String future = LocalDate.now().plusDays(5).toString();
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"멘션 대화 모집\",\"meetDt\":\"" + future + "\"}",
                host).body(), "$.data");
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", mem).statusCode());
        String applyId = jdbc.queryForObject(
                "SELECT apply_id::text FROM recruit_apply WHERE recruit_id = ?::integer AND member_id = 'mcmem'",
                String.class, rid);
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + applyId + "\",\"applyCd\":\"APPLY02\"}", host).statusCode());

        int memBefore = notificationCnt("mcmem");
        int outBefore = notificationCnt("mcout");
        assertEquals(200, post("/api/adm/recruitChat/insertRecruitChat.do",
                "{\"recruitId\":\"" + rid + "\",\"content\":\"@대화멤버 @바깥사람 토요일 괜찮으세요?\"}",
                host).statusCode());

        assertEquals(memBefore + 1, notificationCnt("mcmem"), "대화 멤버는 멘션 알림을 받는다");
        assertEquals(outBefore, notificationCnt("mcout"), "멤버가 아닌 회원은 멘션해도 알림이 가지 않는다");
        assertTrue(true);
    }

    private int notificationCnt(String memberId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE member_id = ? AND use_yn = 'Y'", Integer.class, memberId);
        return n == null ? 0 : n;
    }

    private String lastNotiType(String memberId) {
        return jdbc.queryForObject(
                "SELECT type FROM notification WHERE member_id = ? ORDER BY notification_id DESC LIMIT 1",
                String.class, memberId);
    }
}
