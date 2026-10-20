package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게시글·댓글 추천/비추천.
 *
 * <p>규칙: 한 사람이 한 대상에 <b>한 표</b>(추천 또는 비추천). 같은 쪽을 다시 누르면 취소, 반대쪽을 누르면 전환.
 *
 * <p>조용히 틀릴 수 있는 곳 — 숫자 컬럼(good_cnt·bad_cnt)은 표시용 사본이라 표(content_like)와 어긋나도
 * 에러가 안 난다. 전환할 때 한쪽만 고치거나, 취소가 숫자를 안 내리면 화면 숫자가 계속 틀어진다.
 * 그래서 매 단계 숫자와 표 행을 같이 확인한다.
 */
class LikeTest extends IntegrationTest {

    private static final String VOTE = "/api/adm/like/updateLike.do";

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM content_like WHERE target_type = 'POST' AND target_id IN"
                + " (SELECT post_id FROM post WHERE title LIKE 'ZZ추천%')");
        jdbc.update("DELETE FROM content_like WHERE target_type = 'COMMENT' AND target_id IN"
                + " (SELECT comment_id FROM comment WHERE content LIKE 'ZZ추천%')");
        jdbc.update("DELETE FROM comment WHERE content LIKE 'ZZ추천%'");
        jdbc.update("DELETE FROM post WHERE title LIKE 'ZZ추천%'");
        jdbc.update("DELETE FROM board WHERE name LIKE 'ZZ추천%'");
    }

    @Test
    @DisplayName("추천을 누르면 +1, 다시 누르면 취소된다")
    void goodTogglesOnAndOff() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = newPost(user, "ZZ추천 토글");

        String on = vote(user, "POST", postId, "GOOD").body();
        assertThat((String) JsonPath.read(on, "$.data.likedYn")).isEqualTo("Y");
        assertThat((String) JsonPath.read(on, "$.data.goodCnt")).isEqualTo("1");
        assertThat(postCounts(postId)).isEqualTo("1/0");

        String off = vote(user, "POST", postId, "GOOD").body();
        assertThat((String) JsonPath.read(off, "$.data.likedYn")).isEqualTo("N");
        assertThat((String) JsonPath.read(off, "$.data.goodCnt")).isEqualTo("0");
        assertThat(postCounts(postId)).isEqualTo("0/0");
        assertThat(voteRows("POST", postId)).as("취소하면 표 행도 사라져야 한다").isZero();
    }

    @Test
    @DisplayName("추천 상태에서 비추천을 누르면 전환된다(추천 -1, 비추천 +1)")
    void switchingMovesTheVote() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = newPost(user, "ZZ추천 전환");

        vote(user, "POST", postId, "GOOD");
        String body = vote(user, "POST", postId, "BAD").body();

        assertThat((String) JsonPath.read(body, "$.data.likedYn")).isEqualTo("N");
        assertThat((String) JsonPath.read(body, "$.data.dislikedYn")).isEqualTo("Y");
        // 한쪽만 고치면 "추천 1 · 비추천 1"이 돼 한 사람이 두 표를 던진 것처럼 보인다
        assertThat(postCounts(postId)).isEqualTo("0/1");
        assertThat(voteRows("POST", postId)).isEqualTo(1);

        vote(user, "POST", postId, "BAD");
        assertThat(postCounts(postId)).isEqualTo("0/0");
    }

    @Test
    @DisplayName("여러 사람의 표가 쌓인다(1인 1표)")
    void votesFromDifferentMembersAccumulate() throws Exception {
        String user = accessToken("user", "user1234!");
        String admin = accessToken("admin", "admin1234!");
        String postId = newPost(user, "ZZ추천 누적");

        vote(user, "POST", postId, "GOOD");
        vote(admin, "POST", postId, "GOOD");

        assertThat(postCounts(postId)).isEqualTo("2/0");
        assertThat(voteRows("POST", postId)).isEqualTo(2);
    }

    @Test
    @DisplayName("상세 조회는 내가 누른 상태를 알려준다(사람마다 다르다)")
    void viewShowsMyVote() throws Exception {
        String user = accessToken("user", "user1234!");
        String admin = accessToken("admin", "admin1234!");
        String postId = newPost(user, "ZZ추천 상세");
        vote(user, "POST", postId, "BAD");

        String mine = view(postId, user);
        assertThat((String) JsonPath.read(mine, "$.data.dislikedYn")).isEqualTo("Y");
        assertThat((String) JsonPath.read(mine, "$.data.likedYn")).isEqualTo("N");
        assertThat((String) JsonPath.read(mine, "$.data.badCnt")).isEqualTo("1");

        // 판정 기준(viewerId)을 서버가 넣지 않으면 남의 표가 내 표처럼 보인다
        String others = view(postId, admin);
        assertThat((String) JsonPath.read(others, "$.data.dislikedYn")).isEqualTo("N");
    }

    @Test
    @DisplayName("댓글도 추천/비추천할 수 있고 목록이 내 상태를 알려준다")
    void commentVote() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = newPost(user, "ZZ추천 댓글글");
        assertThat(post("/api/adm/comment/insertComment.do",
                "{\"postId\":\"" + postId + "\",\"content\":\"ZZ추천 댓글\"}", user).statusCode()).isEqualTo(200);
        String commentId = jdbc.queryForObject(
                "SELECT comment_id::text FROM comment WHERE content = 'ZZ추천 댓글'", String.class);

        String body = vote(user, "COMMENT", commentId, "GOOD").body();
        assertThat((String) JsonPath.read(body, "$.data.goodCnt")).isEqualTo("1");

        String list = post("/api/adm/comment/selectCommentList.do", "{\"postId\":\"" + postId + "\"}", user).body();
        assertThat((String) JsonPath.read(list, "$.data[0].likedYn")).isEqualTo("Y");
        assertThat((String) JsonPath.read(list, "$.data[0].goodCnt")).isEqualTo("1");
    }

    @Test
    @DisplayName("비로그인은 투표할 수 없다")
    void guestCannotVote() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = newPost(user, "ZZ추천 비로그인");

        assertThat(vote(null, "POST", postId, "GOOD").statusCode()).isEqualTo(401);
        assertThat(postCounts(postId)).isEqualTo("0/0");
    }

    @Test
    @DisplayName("볼 수 없는 게시판의 글에는 투표할 수 없다")
    void cannotVoteOnInaccessibleBoard() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");
        // GEN 메뉴에 걸리지 않은 게시판 = 관리자만 접근
        Integer boardId = jdbc.queryForObject("INSERT INTO board (name, type_cd, list_cnt,"
                + " reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES ('ZZ추천 비공개', 'BOARD01', 10, 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')"
                + " RETURNING board_id", Integer.class);
        String postId = JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + boardId + "\",\"title\":\"ZZ추천 비공개글\",\"content\":\"본문\"}", admin).body(),
                "$.data");

        // 막지 않으면 글 번호를 찍어 남의 비공개 게시판 글이 있는지 확인하는 통로가 된다
        assertThat(vote(user, "POST", postId, "GOOD").statusCode()).isEqualTo(403);
        assertThat(postCounts(postId)).isEqualTo("0/0");
    }

    @Test
    @DisplayName("없는 대상·삭제된 글·잘못된 값은 거절한다")
    void rejectsInvalidTargets() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = newPost(user, "ZZ추천 검증");

        assertThat(vote(user, "POST", "99999999", "GOOD").statusCode()).isEqualTo(404);
        assertThat(vote(user, "BOARD", postId, "GOOD").statusCode()).isEqualTo(400);
        assertThat(vote(user, "POST", postId, "LOVE").statusCode()).isEqualTo(400);
        assertThat(vote(user, "POST", "abc", "GOOD").statusCode()).as("숫자가 아니면 DB 캐스트 500 전에 막는다")
                .isEqualTo(400);

        assertThat(post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + postId + "\"}", user).statusCode())
                .isEqualTo(200);
        assertThat(vote(user, "POST", postId, "GOOD").statusCode()).as("삭제된 글에 표가 쌓이면 안 된다")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("구분값을 안 보내면 추천으로 처리한다")
    void missingTypeMeansGood() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = newPost(user, "ZZ추천 기본값");

        // 추천 버튼 하나만 둔 화면(파생 프로젝트의 좋아요)이 구분값 없이 불러도 그대로 동작해야 한다
        HttpResponse<String> res = post(VOTE, "{\"targetType\":\"POST\",\"targetId\":\"" + postId + "\"}", user);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(postCounts(postId)).isEqualTo("1/0");
    }

    // ===== helpers =====

    private String newPost(String token, String title) throws Exception {
        return JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"" + title + "\",\"content\":\"본문\"}", token).body(), "$.data");
    }

    private HttpResponse<String> vote(String token, String type, String id, String likeType) throws Exception {
        return post(VOTE, "{\"targetType\":\"" + type + "\",\"targetId\":\"" + id + "\",\"likeType\":\"" + likeType
                + "\"}", token);
    }

    private String view(String postId, String token) throws Exception {
        return post("/api/adm/post/selectPostView.do", "{\"rowId\":\"" + postId + "\",\"viewUp\":\"N\"}", token).body();
    }

    /** "추천/비추천" — 표시용 숫자 컬럼 */
    private String postCounts(String postId) {
        return jdbc.queryForObject("SELECT COALESCE(good_cnt,0) || '/' || COALESCE(bad_cnt,0) FROM post"
                + " WHERE post_id = ?::integer", String.class, Integer.parseInt(postId));
    }

    private int voteRows(String type, String id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM content_like WHERE target_type = ? AND target_id = ?::integer",
                Integer.class, type, Integer.parseInt(id));
        return n == null ? 0 : n;
    }
}
