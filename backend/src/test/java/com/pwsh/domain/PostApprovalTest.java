package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게시 승인(CMS 1.2.41) — 게시판별로 켜면 회원 글은 관리자 승인 전까지 작성자·관리자만 본다.
 *
 * <p>조용히 틀릴 수 있는 곳 — 목록·상세만 가리고 다른 통로(통합검색·댓글·추천·첨부·답글 스레드·비밀번호 열람·메인 화면 최신글)로
 * 승인 전 글이 새는 것, 승인받은 뒤 내용을 바꿔치기하는 것, 반려 사유가 작성자 아닌 사람에게 보이는 것, 그리고 승인을 관리자가 아닌 사람이 하는 것.
 */
class PostApprovalTest extends IntegrationTest {

    /** 기초데이터 공지사항 게시판 — GUEST·MEMBER 열람 권한이 있다. 테스트 동안만 승인을 켠다 */
    private static final String BOARD = "1";
    private static final String KW = "zzapprkw";

    private String admin;
    private String owner;
    private String other;

    @BeforeEach
    void setUp() throws Exception {
        admin = accessToken("admin", "admin1234!");
        owner = accessToken("user", "user1234!");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE member_id = 'zzappr01'", Integer.class) == 0) {
            createMember(admin, "zzappr01");
        }
        post("/api/adm/member/updateMemberAuthGroup.do", "{\"memberId\":\"zzappr01\",\"authGroupIds\":[\"MEMBER\"]}", admin);
        other = accessToken("zzappr01", "Test1234!@");
        jdbc.update("UPDATE board SET approval_yn = 'Y' WHERE board_id = 1");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("UPDATE board SET approval_yn = 'N' WHERE board_id = 1");
        jdbc.update("DELETE FROM notification_setting WHERE member_id = 'user'");
        jdbc.update("DELETE FROM bookmark WHERE target_type = 'POST' AND target_id IN (SELECT post_id FROM post WHERE title LIKE '%ZZ승인%')");
        jdbc.update("DELETE FROM report WHERE target_type = 'POST' AND target_id IN (SELECT post_id FROM post WHERE title LIKE '%ZZ승인%')");
        jdbc.update("DELETE FROM member_hobby WHERE hobby_id IN (SELECT hobby_id FROM hobby WHERE name LIKE 'ZZ승인%')");
        jdbc.update("DELETE FROM board WHERE name LIKE 'ZZ승인%'");
        jdbc.update("DELETE FROM comment WHERE post_id IN (SELECT post_id FROM post WHERE title LIKE '%ZZ승인%')");
        jdbc.update("DELETE FROM content_like WHERE target_type = 'POST' AND target_id IN (SELECT post_id FROM post WHERE title LIKE '%ZZ승인%')");
        jdbc.update("DELETE FROM notification WHERE type = 'POST_APPROVAL' OR content LIKE '%ZZ승인%'");
        jdbc.update("DELETE FROM post WHERE title LIKE '%ZZ승인%'");
        jdbc.update("DELETE FROM hobby WHERE name LIKE 'ZZ승인%'");
        jdbc.update("DELETE FROM auth WHERE conn_id = 'MEMBER' AND menu_id IN (SELECT menu_id FROM menu WHERE link_url = '/adm/postapproval')");
    }

    @Test
    @DisplayName("승인을 쓰지 않는 게시판의 회원 글은 바로 게시된다(기존 동작)")
    void approvalOffPublishesImmediately() throws Exception {
        jdbc.update("UPDATE board SET approval_yn = 'N' WHERE board_id = 1");
        String id = newPost(owner, "ZZ승인 끔 " + KW);
        assertThat(column(id, "approval_cd")).isEqualTo("APPROVAL02");
        assertThat(listIds(null)).contains(id);
    }

    @Test
    @DisplayName("승인 대기 글은 작성자·관리자만 본다 — 목록·건수·상세·통합검색·댓글·추천 어디로도 다른 사람에게 새지 않는다")
    void pendingPostIsHiddenEverywhere() throws Exception {
        String id = newPost(owner, "ZZ승인 대기 " + KW);
        assertThat(column(id, "approval_cd")).isEqualTo("APPROVAL01");

        assertThat(listIds(null)).as("비로그인(메인 화면 최신글도 이 목록)").doesNotContain(id);
        assertThat(listIds(other)).as("다른 회원").doesNotContain(id);
        assertThat(listIds(owner)).as("작성자").contains(id);
        assertThat(listIds(admin)).as("관리자").contains(id);
        assertThat(listField(owner, id, "approvalCd")).isEqualTo("APPROVAL01");
        assertThat(totalCount(null)).as("건수도 목록과 같게").isEqualTo(totalCount(other));
        assertThat(totalCount(owner)).isEqualTo(totalCount(null) + 1);

        assertThat(viewTitle(null, id)).as("비로그인 상세").isNull();
        assertThat(viewTitle(other, id)).as("다른 회원 상세").isNull();
        assertThat(viewTitle(owner, id)).contains("ZZ승인 대기");
        assertThat(viewTitle(admin, id)).contains("ZZ승인 대기");

        assertThat(JsonPath.<Integer>read(post("/api/adm/search/selectSearchAll.do", "{\"filterKeyword\":\"" + KW + "\"}", owner).body(),
                "$.data.postCount")).as("통합검색은 공개된 글만(작성자에게도)").isZero();

        assertThat(post("/api/adm/comment/selectCommentList.do", "{\"postId\":\"" + id + "\"}", other).statusCode()).isEqualTo(403);
        assertThat(comment(other, id)).as("다른 회원 댓글").isEqualTo(403);
        assertThat(post("/api/adm/like/updateLike.do", "{\"targetType\":\"POST\",\"targetId\":\"" + id + "\",\"likeType\":\"GOOD\"}", other)
                .statusCode()).as("다른 회원 추천").isEqualTo(403);
        assertThat(comment(owner, id)).as("작성자는 자기 글에 댓글을 달 수 있다").isEqualTo(200);
    }

    @Test
    @DisplayName("승인 대기 비밀글은 비밀번호를 알아도 열리지 않는다")
    void pendingSecretPostCannotBeOpenedWithPassword() throws Exception {
        String id = newPost(owner, "ZZ승인 비밀 " + KW, ",\"secretYn\":\"Y\",\"password\":\"pw1234\"");
        String body = post("/api/adm/post/selectPostView.do", "{\"rowId\":\"" + id + "\",\"password\":\"pw1234\"}", other).body();
        assertThat(nullable(body, "$.data")).isNull();
    }

    @Test
    @DisplayName("관리자 글은 승인을 쓰는 게시판에서도 바로 게시된다")
    void adminPostSkipsApproval() throws Exception {
        String id = newPost(admin, "ZZ승인 관리자 " + KW);
        assertThat(column(id, "approval_cd")).isEqualTo("APPROVAL02");
        assertThat(listIds(null)).contains(id);
        assertThat(approvalListIds("")).as("처음부터 공개된 글은 승인 화면(전체)에 없다 — 있으면 '승인' 목록이 사이트 전체 글이 된다")
                .doesNotContain(id);
    }

    @Test
    @DisplayName("반려(사유 필수)·승인 — 작성자에게 알림, 반려 사유는 작성자·관리자만, 이미 처리한 글은 다시 처리하지 않는다")
    void rejectThenApprove() throws Exception {
        String id = newPost(owner, "ZZ승인 처리 " + KW);
        // 이 저장소는 알림 수신 설정이 있다 — 다 꺼 둔 작성자도 승인·반려 결과는 받는다(폼 결과와 같다)
        jdbc.update("INSERT INTO notification_setting (member_id, apply_yn, comment_yn, message_yn, review_yn, upd_dt)"
                + " VALUES ('user', 'N', 'N', 'N', 'N', NOW()) ON CONFLICT (member_id) DO UPDATE"
                + " SET apply_yn = 'N', comment_yn = 'N', message_yn = 'N', review_yn = 'N'");

        String queue = post("/api/adm/postapproval/selectPostApprovalList.do", "{\"approvalCd\":\"APPROVAL01\",\"pageSize\":100}", admin).body();
        assertThat(JsonPath.<List<String>>read(queue, "$.data.list[*].rowId")).contains(id);
        assertThat(JsonPath.<List<String>>read(queue, "$.data.list[?(@.rowId=='" + id + "')].boardName")).containsExactly("공지사항");
        assertThat(JsonPath.<String>read(post("/api/adm/postapproval/selectPostApprovalView.do", "{\"rowId\":\"" + id + "\"}", admin).body(),
                "$.data.content")).isEqualTo("본문");

        assertThat(decide(id, "APPROVAL03", "")).as("반려 사유 없음").isEqualTo(400);
        assertThat(decide(id, "APPROVAL01", "x")).as("대기로 되돌리기는 처리가 아니다").isEqualTo(400);
        assertThat(decide(id, "ZZ", "x")).isEqualTo(400);
        assertThat(decide(id, "APPROVAL03", "ZZ사유 광고 문구")).isEqualTo(200);
        assertThat(column(id, "approval_cd")).isEqualTo("APPROVAL03");
        assertThat(column(id, "approval_id")).isEqualTo("admin");
        assertThat(decide(id, "APPROVAL02", "")).as("반려된 글은 작성자가 고치기 전엔 다시 처리하지 않는다").isEqualTo(400);

        String mine = post("/api/adm/post/selectPostView.do", "{\"rowId\":\"" + id + "\"}", owner).body();
        assertThat(JsonPath.<String>read(mine, "$.data.approvalCd")).isEqualTo("APPROVAL03");
        assertThat(JsonPath.<String>read(mine, "$.data.approvalMemo")).isEqualTo("ZZ사유 광고 문구");
        assertThat(viewTitle(other, id)).as("반려된 글도 남에겐 없다").isNull();
        assertThat(lastNotice()).contains("반려").contains("ZZ사유 광고 문구");

        // 작성자가 고치면 다시 승인 대기 — 반려 사유는 지운다
        assertThat(update(owner, id, "ZZ승인 처리 고침 " + KW)).isEqualTo(200);
        assertThat(column(id, "approval_cd")).isEqualTo("APPROVAL01");
        assertThat(column(id, "approval_memo")).isNull();

        assertThat(decide(id, "APPROVAL02", "")).isEqualTo(200);
        assertThat(listIds(null)).as("승인되면 모두에게").contains(id);
        assertThat(approvalListIds("APPROVAL01")).as("대기 목록에서 빠진다").doesNotContain(id);
        assertThat(approvalListIds("APPROVAL02")).as("승인 목록에 남는다").contains(id);
        assertThat(lastNotice()).contains("승인");
        assertThat(jdbc.queryForObject("SELECT link_url FROM notification WHERE member_id = 'user' AND type = 'POST_APPROVAL'"
                + " ORDER BY notification_id DESC LIMIT 1", String.class)).isEqualTo("/gen/board/1?post=" + id);
    }

    @Test
    @DisplayName("승인된 글을 작성자가 고치면 다시 승인 대기 — 관리자가 고치면 상태를 건드리지 않는다")
    void editAfterApprovalNeedsApprovalAgain() throws Exception {
        String id = newPost(owner, "ZZ승인 수정 " + KW);
        assertThat(decide(id, "APPROVAL02", "")).isEqualTo(200);
        assertThat(update(admin, id, "ZZ승인 수정 관리자 " + KW)).isEqualTo(200);
        assertThat(column(id, "approval_cd")).as("관리자 수정").isEqualTo("APPROVAL02");

        assertThat(update(owner, id, "ZZ승인 수정 바꿔치기 " + KW)).isEqualTo(200);
        assertThat(column(id, "approval_cd")).as("작성자 수정").isEqualTo("APPROVAL01");
        assertThat(listIds(null)).doesNotContain(id);
        assertThat(update(admin, id, "ZZ승인 수정 관리자2 " + KW)).isEqualTo(200);
        assertThat(column(id, "approval_cd")).as("관리자가 대기 글을 고쳐도 대기").isEqualTo("APPROVAL01");

        // 승인을 끈 게시판에서 작성자가 고치면 바로 게시(대기·반려에 묶이지 않는다)
        jdbc.update("UPDATE board SET approval_yn = 'N' WHERE board_id = 1");
        assertThat(update(owner, id, "ZZ승인 수정 끈 뒤 " + KW)).isEqualTo(200);
        assertThat(column(id, "approval_cd")).isEqualTo("APPROVAL02");
    }

    @Test
    @DisplayName("답글도 게시판 규칙을 따른다 — 승인 전 답글은 스레드에서도 숨고, 볼 수 없는 대기 글엔 답글을 달 수 없다")
    void repliesFollowTheRule() throws Exception {
        String pending = newPost(owner, "ZZ승인 원글 대기 " + KW);
        assertThat(post("/api/adm/post/insertPost.do", "{\"pPostId\":\"" + pending + "\",\"boardId\":\"" + BOARD
                + "\",\"title\":\"RE: ZZ승인\",\"content\":\"본문\"}", other).statusCode()).as("남의 대기 글에 답글").isEqualTo(404);

        String root = newPost(admin, "ZZ승인 원글 공개 " + KW);
        HttpResponse<String> r = post("/api/adm/post/insertPost.do", "{\"pPostId\":\"" + root + "\",\"boardId\":\"" + BOARD
                + "\",\"title\":\"RE: ZZ승인 답글\",\"content\":\"본문\"}", other);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        String reply = JsonPath.read(r.body(), "$.data");
        assertThat(column(reply, "approval_cd")).isEqualTo("APPROVAL01");
        assertThat(listIds(null)).contains(root).doesNotContain(reply);
        assertThat(listIds(owner)).as("다른 회원의 대기 답글").doesNotContain(reply);
        assertThat(listIds(other)).as("작성자는 자기 답글을 본다").contains(reply);
    }

    @Test
    @DisplayName("승인 화면은 관리자 메뉴 권한이 있어야 한다 — 비로그인 401, 일반 회원 403")
    void approvalNeedsAdminMenu() throws Exception {
        String id = newPost(owner, "ZZ승인 권한 " + KW);
        assertThat(post("/api/adm/postapproval/selectPostApprovalList.do", "{}", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/adm/postapproval/selectPostApprovalList.do", "{}", owner).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/postapproval/selectPostApprovalView.do", "{\"rowId\":\"" + id + "\"}", owner).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/postapproval/updatePostApproval.do",
                "{\"rowId\":\"" + id + "\",\"approvalCd\":\"APPROVAL02\"}", owner).statusCode()).isEqualTo(403);
        assertThat(column(id, "approval_cd")).isEqualTo("APPROVAL01");
    }

    @Test
    @DisplayName("게시판 설정에서 게시 승인을 켜고 끈다 — 새 게시판은 꺼진 채로 만들어진다")
    void boardSetting() throws Exception {
        assertThat(post("/api/adm/board/insertBoard.do", "{\"boardName\":\"ZZ승인 게시판\",\"typeCd\":\"BOARD01\"}", admin).statusCode())
                .isEqualTo(200);
        String boardId = jdbc.queryForObject("SELECT board_id::text FROM board WHERE name = 'ZZ승인 게시판'", String.class);
        assertThat(boardField(boardId, "approvalYn")).isEqualTo("N");
        assertThat(post("/api/adm/board/updateBoard.do", "{\"rowId\":\"" + boardId + "\",\"boardName\":\"ZZ승인 게시판\","
                + "\"typeCd\":\"BOARD01\",\"approvalYn\":\"Y\"}", admin).statusCode()).isEqualTo(200);
        assertThat(boardField(boardId, "approvalYn")).isEqualTo("Y");
        assertThat(nullable(post("/api/adm/board/selectBoardView.do", "{\"rowId\":\"" + BOARD + "\"}", null).body(), "$.data.approvalYn"))
                .as("사용자 화면(비로그인 포함)이 이 값으로 '승인 후 게시' 안내를 한다").isEqualTo("Y");
    }

    @Test
    @DisplayName("이 저장소의 다른 통로도 승인 전 글을 숨긴다 — 취미 게시판(공개)·이번 주 베스트·공개 프로필·피드·취미 글 수·신고·북마크 제목, 내 글 목록엔 상태와 함께")
    void pwshPathsHidePending() throws Exception {
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do", "{\"hobbyName\":\"ZZ승인취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        String hobbyBoard = jdbc.queryForObject("SELECT board_id::text FROM hobby WHERE hobby_id = ?::integer", String.class, hobbyId);
        jdbc.update("UPDATE board SET approval_yn = 'Y' WHERE board_id = ?::integer", hobbyBoard);
        assertThat(post("/api/adm/memberHobby/insertMemberHobby.do", "{\"hobbyId\":\"" + hobbyId + "\"}", other).statusCode()).isEqualTo(200);

        HttpResponse<String> r = post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + hobbyBoard + "\",\"title\":\"ZZ승인 취미글 " + KW + "\",\"content\":\"본문\"}", owner);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        String id = JsonPath.read(r.body(), "$.data");
        assertThat(column(id, "approval_cd")).isEqualTo("APPROVAL01");
        jdbc.update("UPDATE post SET view_cnt = 1000000 WHERE post_id = ?::integer", Integer.parseInt(id)); // 들어가면 베스트 1위가 되도록

        assertThat(post("/api/adm/comment/selectCommentList.do", "{\"postId\":\"" + id + "\"}", null).statusCode())
                .as("취미 게시판은 비로그인 공개지만 승인 전 글은 아니다").isEqualTo(403);
        assertThat(JsonPath.<List<String>>read(post("/api/adm/post/selectPostListWeeklyBest.do", "{}", null).body(), "$.data[*].rowId"))
                .as("이번 주 베스트").doesNotContain(id);
        String handle = jdbc.queryForObject("SELECT handle FROM member WHERE member_id = 'user'", String.class);
        assertThat(JsonPath.<List<String>>read(post("/api/auth/memberProfile", "{\"handle\":\"" + handle + "\"}", other).body(),
                "$.data.posts[*].rowId")).as("공개 프로필").doesNotContain(id);
        assertThat(JsonPath.<List<String>>read(post("/api/adm/feed/selectFeedList.do", "{\"pageNo\":1,\"pageSize\":50}", other).body(),
                "$.data.list[?(@.feedType=='POST')].rowId")).as("담은 취미 피드").doesNotContain(id);
        assertThat(hobbyPostCnt()).as("취미 글 수").isEqualTo("0");
        assertThat(post("/api/adm/report/insertReport.do", "{\"targetType\":\"POST\",\"targetId\":\"" + id
                + "\",\"reason\":\"ZZ승인\",\"reasonCd\":\"REPORT01\"}", other).statusCode()).as("볼 수 없는 글은 신고 대상도 없다").isEqualTo(404);
        assertThat(JsonPath.<List<String>>read(post("/api/adm/post/selectPostListMine.do", "{}", owner).body(),
                "$.data[?(@.rowId=='" + id + "')].approvalCd")).as("내 글 목록엔 상태와 함께").containsExactly("APPROVAL01");

        assertThat(decide(id, "APPROVAL02", "")).isEqualTo(200);
        assertThat(hobbyPostCnt()).isEqualTo("1");
        assertThat(JsonPath.<List<String>>read(post("/api/adm/feed/selectFeedList.do", "{\"pageNo\":1,\"pageSize\":50}", other).body(),
                "$.data.list[?(@.feedType=='POST')].rowId")).as("승인되면 피드에").contains(id);

        // 승인된 글을 북마크한 뒤 작성자가 바꿔 다시 대기가 되면, 북마크 목록에 바뀐 제목이 새지 않는다
        assertThat(JsonPath.<String>read(post("/api/adm/bookmark/updateBookmarkToggle.do",
                "{\"targetType\":\"POST\",\"targetId\":\"" + id + "\"}", other).body(), "$.data.markedYn")).isEqualTo("Y");
        assertThat(update(owner, id, "ZZ승인 바뀐제목 " + KW)).isEqualTo(200);
        String marks = post("/api/adm/bookmark/selectBookmarkList.do", "{\"targetType\":\"POST\"}", other).body();
        assertThat(marks).doesNotContain("ZZ승인 바뀐제목").contains("공개되지 않은 글입니다.");
    }

    // ===== helpers =====

    private String hobbyPostCnt() throws Exception {
        List<String> v = JsonPath.read(post("/api/adm/hobby/selectHobbyList.do", "{\"filterKeyword\":\"ZZ승인취미\",\"pageSize\":10}", null).body(),
                "$.data.list[?(@.hobbyName=='ZZ승인취미')].postCnt");
        assertThat(v).hasSize(1);
        return String.valueOf(v.get(0));
    }

    private String newPost(String token, String title) throws Exception {
        return newPost(token, title, "");
    }

    private String newPost(String token, String title, String extra) throws Exception {
        HttpResponse<String> r = post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + BOARD + "\",\"title\":\"" + title + "\",\"content\":\"본문\"" + extra + "}", token);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return JsonPath.read(r.body(), "$.data");
    }

    private int update(String token, String id, String title) throws Exception {
        return post("/api/adm/post/updatePost.do", "{\"rowId\":\"" + id + "\",\"title\":\"" + title + "\",\"content\":\"본문\"}", token)
                .statusCode();
    }

    private int decide(String id, String approvalCd, String memo) throws Exception {
        return post("/api/adm/postapproval/updatePostApproval.do",
                "{\"rowId\":\"" + id + "\",\"approvalCd\":\"" + approvalCd + "\",\"approvalMemo\":\"" + memo + "\"}", admin).statusCode();
    }

    private List<String> approvalListIds(String approvalCd) throws Exception {
        return JsonPath.read(post("/api/adm/postapproval/selectPostApprovalList.do",
                "{\"approvalCd\":\"" + approvalCd + "\",\"pageSize\":200}", admin).body(), "$.data.list[*].rowId");
    }

    private int comment(String token, String id) throws Exception {
        return post("/api/adm/comment/insertComment.do", "{\"postId\":\"" + id + "\",\"content\":\"ZZ승인 댓글\"}", token).statusCode();
    }

    private String listBody(String token) throws Exception {
        HttpResponse<String> r = post("/api/adm/post/selectPostList.do", "{\"boardId\":\"" + BOARD + "\",\"pageSize\":\"200\"}", token);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return r.body();
    }

    private List<String> listIds(String token) throws Exception {
        return JsonPath.read(listBody(token), "$.data.list[*].rowId");
    }

    private String listField(String token, String id, String field) throws Exception {
        List<String> v = JsonPath.read(listBody(token), "$.data.list[?(@.rowId=='" + id + "')]." + field);
        return v.isEmpty() ? null : v.get(0);
    }

    private int totalCount(String token) throws Exception {
        return JsonPath.read(listBody(token), "$.data.totalCount");
    }

    private String viewTitle(String token, String id) throws Exception {
        return nullable(post("/api/adm/post/selectPostView.do", "{\"rowId\":\"" + id + "\"}", token).body(), "$.data.title");
    }

    /** 새 게시판은 사용자 메뉴에 안 걸려 있어 비로그인은 못 읽는다 — 관리자로 읽는다(비로그인 확인은 공지사항 게시판으로) */
    private String boardField(String boardId, String field) throws Exception {
        return nullable(post("/api/adm/board/selectBoardView.do", "{\"rowId\":\"" + boardId + "\"}", admin).body(), "$.data." + field);
    }

    /** 응답은 null 필드를 싣지 않는다(상세가 null이면 data 자체가 없다) — 없으면 null로 읽는다 */
    private String nullable(String body, String path) {
        Object v = JsonPath.using(Configuration.defaultConfiguration()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)).parse(body).read(path);
        return v == null ? null : String.valueOf(v);
    }

    private String lastNotice() {
        return jdbc.queryForObject("SELECT content FROM notification WHERE member_id = 'user' AND type = 'POST_APPROVAL'"
                + " ORDER BY notification_id DESC LIMIT 1", String.class);
    }

    private String column(String postId, String col) {
        return jdbc.queryForObject("SELECT " + col + " FROM post WHERE post_id = ?::integer", String.class, Integer.parseInt(postId));
    }
}
