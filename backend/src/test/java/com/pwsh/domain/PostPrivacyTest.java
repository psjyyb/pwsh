package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게시글 단위 비공개 — 비밀글(1:1 문의 포함)의 댓글·첨부·목록 제목, 삭제된 글, 공지 고정 권한.
 *
 * <p>왜 이 테스트가 있나. 게시글 접근 판정(GenAccessGuard.checkPost·canAccessPost)이 v0.6.32(CMS 1.2.32)까지 <b>게시판 권한만</b> 봤고, 취미 게시판은 그 판정조차 건너뛰었다.
 * 상세만 비밀글을 가리고, 그 글의 댓글·첨부·목록 제목은 게시판 권한만으로 열려 1:1 문의 답변·첨부를 다른 회원이 받을 수 있었다.
 * 규칙(CMS 1.2.33, v0.6.33): 비밀글의 댓글·첨부는 <b>작성자·관리자만</b>(비밀번호로 연 사람은 본문만), 목록 제목은 "비밀글입니다.",
 * 1:1 문의는 서버가 비밀글로 강제, 삭제된 글의 댓글·첨부는 막힘, 공지 고정은 관리자·공지 쓰는 게시판만.
 */
class PostPrivacyTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    /** 기초데이터: 1=공지사항(공개·공지 사용·첨부 사용), 3=갤러리(공지 미사용), 4=1:1문의(회원 전용) */
    private static final String NOTICE_BOARD = "1";
    private static final String GALLERY_BOARD = "3";
    private static final String QNA_BOARD = "4";

    private String admin;
    private String owner;
    private String other;

    @BeforeEach
    void tokens() throws Exception {
        admin = accessToken("admin", "admin1234!");
        owner = accessToken("user", "user1234!");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE member_id = 'zzpriv01'", Integer.class) == 0) {
            createMember(admin, "zzpriv01");
        }
        // 그룹이 없는 회원은 게시판 권한 자체가 없어(비로그인보다 좁다) 비밀글 규칙을 확인할 수 없다 — 일반 회원(user)과 같은 MEMBER 그룹
        post("/api/adm/member/updateMemberAuthGroup.do", "{\"memberId\":\"zzpriv01\",\"authGroupIds\":[\"MEMBER\"]}", admin);
        other = accessToken("zzpriv01", "Test1234!@");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM bookmark WHERE target_type = 'POST'"
                + " AND target_id IN (SELECT post_id FROM post WHERE title LIKE 'ZZ사생활%')");
        jdbc.update("DELETE FROM comment WHERE post_id IN (SELECT post_id FROM post WHERE title LIKE 'ZZ사생활%')");
        jdbc.update("DELETE FROM file_ref WHERE map_key IN (SELECT post_id FROM post WHERE title LIKE 'ZZ사생활%')"
                + " AND file_type LIKE 'POST%'");
        jdbc.update("DELETE FROM post WHERE title LIKE 'ZZ사생활%'");
        jdbc.update("DELETE FROM file WHERE original_name LIKE 'zzpriv%'");
    }

    // ===== S1 비밀글 댓글 =====

    @Test
    @DisplayName("비밀글의 댓글은 작성자·관리자만 읽고 쓴다 — 비밀번호를 아는 사람·비로그인도 403")
    void secretPostCommentsOnlyForOwnerAndAdmin() throws Exception {
        String postId = secretPost(owner, NOTICE_BOARD, "ZZ사생활 비밀글");
        assertThat(comment(owner, postId, "작성자 댓글")).isEqualTo(200);
        assertThat(comment(admin, postId, "관리자 답변")).isEqualTo(200);

        assertThat(commentList(null, postId).statusCode()).as("비로그인(공개 게시판)").isEqualTo(403);
        assertThat(commentList(other, postId).statusCode()).as("다른 회원").isEqualTo(403);
        assertThat(comment(other, postId, "끼어들기")).as("다른 회원이 남의 비밀글에 댓글").isEqualTo(403);

        assertThat((List<String>) JsonPath.read(commentList(owner, postId).body(), "$.data[*].content")).hasSize(2);
        assertThat(commentList(admin, postId).statusCode()).isEqualTo(200);
    }

    // ===== S2 비밀글 첨부 =====

    @Test
    @DisplayName("비밀글의 첨부는 작성자·관리자만 — 목록·내려받기·공개 이미지 모두")
    void secretPostFilesOnlyForOwnerAndAdmin() throws Exception {
        String postId = secretPost(owner, NOTICE_BOARD, "ZZ사생활 첨부");
        String fileId = upload(owner, "zzpriv-doc.png");
        assertThat(map(owner, postId, fileId)).isEqualTo(200);

        assertThat(fileList(other, postId)).as("다른 회원 첨부 목록").isEqualTo(403);
        assertThat(get("/api/adm/file/download.do?fileId=" + fileId, other).statusCode()).isEqualTo(403);
        assertThat(get("/api/pub/image/" + fileId, null).statusCode()).as("비로그인 공개 이미지").isEqualTo(403);

        assertThat(fileList(owner, postId)).isEqualTo(200);
        assertThat(get("/api/adm/file/download.do?fileId=" + fileId, owner).statusCode()).isEqualTo(200);
        assertThat(get("/api/adm/file/download.do?fileId=" + fileId, admin).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("1:1 문의 첨부·답변을 다른 회원이 받지 못한다")
    void qnaAnswerAndFilesHidden() throws Exception {
        String postId = newPost(owner, QNA_BOARD, "ZZ사생활 1:1", "{}");
        assertThat(comment(admin, postId, "관리자 답변")).isEqualTo(200);
        String fileId = upload(owner, "zzpriv-qna.png");
        assertThat(map(owner, postId, fileId)).isEqualTo(200);

        assertThat(commentList(other, postId).statusCode()).isEqualTo(403);
        assertThat(get("/api/adm/file/download.do?fileId=" + fileId, other).statusCode()).isEqualTo(403);
        assertThat(commentList(owner, postId).statusCode()).isEqualTo(200);
    }

    // ===== S4 목록 제목 =====

    @Test
    @DisplayName("목록에서 남의 비밀글 제목은 '비밀글입니다.'로 보이고, 제목 검색에도 걸리지 않는다")
    void secretTitleMaskedInList() throws Exception {
        String title = "ZZ사생활 숨길제목";
        String postId = secretPost(owner, NOTICE_BOARD, title);

        assertThat(listTitle(other, postId)).isEqualTo("비밀글입니다.");
        assertThat(listTitle(null, postId)).isEqualTo("비밀글입니다.");
        assertThat(listTitle(owner, postId)).isEqualTo(title);
        assertThat(listTitle(admin, postId)).isEqualTo(title);

        // 가려도 제목 검색 결과에 남으면 "그 단어가 든 비밀글이 있다"가 새어 나간다(상세·통합검색과 같은 규칙)
        assertThat(searchCount(other, "숨길제목")).isZero();
        assertThat(searchCount(owner, "숨길제목")).isEqualTo(1);
    }

    @Test
    @DisplayName("갤러리 목록에서 남의 비밀글은 대표 이미지 ID를 주지 않는다(받아도 403이라 깨진 이미지가 된다)")
    void secretGalleryThumbHidden() throws Exception {
        // 이 저장소는 일반 회원이 갤러리 게시판(3)에 글을 못 쓴다 — 대표 이미지는 게시판 종류와 무관하게 POST_IMG 첫 장이라 취미 게시판으로 본다
        String postId = secretPost(owner, hobbyBoard(), "ZZ사생활 갤러리비밀");
        String fileId = upload(owner, "zzpriv-thumb.png");
        assertThat(post("/api/adm/file/saveFileMapping.do",
                "{\"mapKey\":\"" + postId + "\",\"fileType\":\"POST_IMG\",\"fileIds\":[\"" + fileId + "\"]}", owner)
                .statusCode()).isEqualTo(200);

        assertThat(thumb(other, postId)).isEmpty();
        assertThat(thumb(owner, postId)).containsExactly(fileId);
    }

    private String hobbyBoard() {
        return jdbc.queryForObject("SELECT board_id::text FROM hobby WHERE use_yn = 'Y' ORDER BY hobby_id LIMIT 1", String.class);
    }

    private List<String> thumb(String token, String postId) throws Exception {
        String body = post("/api/adm/post/selectPostList.do",
                "{\"boardId\":\"" + hobbyBoard() + "\",\"pageSize\":\"200\"}", token).body();
        return JsonPath.read(body, "$.data.list[?(@.rowId=='" + postId + "')].fileId");
    }

    // ===== S5 1:1 문의 비밀글 강제 =====

    @Test
    @DisplayName("1:1 문의는 요청과 상관없이 비밀글로 저장되고 공지가 될 수 없다")
    void qnaIsAlwaysSecret() throws Exception {
        String postId = newPost(owner, QNA_BOARD, "ZZ사생활 1:1 공개시도", "{\"secretYn\":\"N\",\"noticeYn\":\"Y\"}");
        assertThat(column(postId, "secret_yn")).isEqualTo("Y");
        assertThat(column(postId, "notice_yn")).isEqualTo("N");

        assertThat(update(owner, postId, "ZZ사생활 1:1 공개시도", "\"secretYn\":\"N\"")).isEqualTo(200);
        assertThat(column(postId, "secret_yn")).as("수정으로도 풀 수 없다").isEqualTo("Y");
    }

    // ===== S6 삭제된 글 =====

    @Test
    @DisplayName("삭제된 글의 댓글은 조회되지 않는다(취미 게시판 포함)")
    void deletedPostCommentsHidden() throws Exception {
        String postId = newPost(owner, NOTICE_BOARD, "ZZ사생활 삭제글", "{}");
        assertThat(comment(other, postId, "댓글")).isEqualTo(200);
        assertThat(post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + postId + "\"}", owner).statusCode()).isEqualTo(200);

        assertThat(commentList(other, postId).statusCode()).isEqualTo(403);
        assertThat(commentList(null, postId).statusCode()).isEqualTo(403);

        // 취미 게시판은 메뉴 권한 쿼리를 타지 않아 GenAccessGuard가 삭제 여부를 따로 본다
        String hobbyPostId = newPost(owner, hobbyBoard(), "ZZ사생활 취미삭제글", "{}");
        assertThat(comment(other, hobbyPostId, "댓글")).isEqualTo(200);
        assertThat(post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + hobbyPostId + "\"}", owner).statusCode())
                .isEqualTo(200);
        assertThat(commentList(null, hobbyPostId).statusCode()).as("취미 게시판 삭제글").isEqualTo(403);
    }

    // ===== S7 공지 고정 =====

    @Test
    @DisplayName("공지 고정은 관리자만, 공지를 쓰는 게시판에서만 — 회원이 보낸 공지는 무시된다")
    void noticeOnlyByAdminOnNoticeBoard() throws Exception {
        String memberPost = newPost(owner, NOTICE_BOARD, "ZZ사생활 회원공지", "{\"noticeYn\":\"Y\"}");
        assertThat(column(memberPost, "notice_yn")).as("회원은 공지를 걸 수 없다").isEqualTo("N");

        String adminPost = newPost(admin, NOTICE_BOARD, "ZZ사생활 관리자공지", "{\"noticeYn\":\"Y\"}");
        assertThat(column(adminPost, "notice_yn")).isEqualTo("Y");

        String galleryPost = newPost(admin, GALLERY_BOARD, "ZZ사생활 갤러리공지", "{\"noticeYn\":\"Y\"}");
        assertThat(column(galleryPost, "notice_yn")).as("공지를 안 쓰는 게시판").isEqualTo("N");

        // 관리자가 회원 글을 공지로 올린 뒤 회원이 수정해도(화면은 공지 칸을 안 보여 'N'을 보낸다) 공지가 풀리지 않는다
        assertThat(update(admin, memberPost, "ZZ사생활 회원공지", "\"noticeYn\":\"Y\"")).isEqualTo(200);
        assertThat(update(owner, memberPost, "ZZ사생활 회원공지 수정", "\"noticeYn\":\"N\"")).isEqualTo(200);
        assertThat(column(memberPost, "notice_yn")).isEqualTo("Y");
    }

    // ===== pwsh 전용: 취미 게시판 · 북마크 =====

    @Test
    @DisplayName("취미 게시판(비로그인 공개)의 비밀글도 댓글·첨부는 작성자·관리자만")
    void hobbyBoardSecretPostProtected() throws Exception {
        // 취미 게시판은 메뉴 권한과 무관하게 공개라 GenAccessGuard가 먼저 통과시킨다 — 그 분기에도 비밀글·삭제글 확인이 있어야 한다
        String postId = secretPost(owner, hobbyBoard(), "ZZ사생활 취미비밀");
        assertThat(comment(owner, postId, "작성자 댓글")).isEqualTo(200);
        String fileId = upload(owner, "zzpriv-hobby.png");
        assertThat(map(owner, postId, fileId)).isEqualTo(200);

        assertThat(commentList(null, postId).statusCode()).as("비로그인").isEqualTo(403);
        assertThat(commentList(other, postId).statusCode()).as("다른 회원").isEqualTo(403);
        assertThat(get("/api/pub/image/" + fileId, null).statusCode()).as("비로그인 공개 이미지").isEqualTo(403);
        assertThat(commentList(owner, postId).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("남의 비밀글은 북마크할 수 없고, 북마크 뒤 비밀글이 된 글은 내 북마크에서 제목이 가려진다")
    void bookmarkCannotRevealSecretTitle() throws Exception {
        // 북마크가 대상 접근권을 안 보면 글 번호만으로 남의 비밀글·1:1 문의를 담아 '내 북마크'에서 실제 제목을 읽는다(v0.6.32까지)
        String secretId = secretPost(owner, NOTICE_BOARD, "ZZ사생활 북마크비밀");
        String qnaId = newPost(owner, QNA_BOARD, "ZZ사생활 북마크문의", "{}");
        assertThat(bookmark(other, secretId)).isEqualTo(403);
        assertThat(bookmark(other, qnaId)).isEqualTo(403);
        // 작성자는 자기 비밀글을 담고, 내 북마크에서 실제 제목을 본다
        assertThat(bookmark(owner, secretId)).isEqualTo(200);
        assertThat((List<String>) JsonPath.read(post("/api/adm/bookmark/selectBookmarkList.do",
                "{\"targetType\":\"POST\"}", owner).body(), "$.data[?(@.targetId=='" + secretId + "')].title"))
                .containsExactly("ZZ사생활 북마크비밀");

        String openId = newPost(owner, NOTICE_BOARD, "ZZ사생활 나중에비밀", "{}");
        assertThat(bookmark(other, openId)).isEqualTo(200);
        assertThat(update(owner, openId, "ZZ사생활 나중에비밀", "\"secretYn\":\"Y\",\"password\":\"pw1234\"")).isEqualTo(200);
        List<String> titles = JsonPath.read(post("/api/adm/bookmark/selectBookmarkList.do", "{\"targetType\":\"POST\"}", other)
                .body(), "$.data[?(@.targetId=='" + openId + "')].title");
        assertThat(titles).containsExactly("비밀글입니다.");
    }

    private int bookmark(String token, String postId) throws Exception {
        return post("/api/adm/bookmark/updateBookmarkToggle.do",
                "{\"targetType\":\"POST\",\"targetId\":\"" + postId + "\"}", token).statusCode();
    }

    // ===== helpers =====

    private String secretPost(String token, String boardId, String title) throws Exception {
        return newPost(token, boardId, title, "{\"secretYn\":\"Y\",\"password\":\"pw1234\"}");
    }

    /** extraJson: {"secretYn":..} 형태(빈 객체 가능) — boardId·title·content에 합친다 */
    private String newPost(String token, String boardId, String title, String extraJson) throws Exception {
        String extra = extraJson.substring(1, extraJson.length() - 1);
        HttpResponse<String> r = post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + boardId + "\",\"title\":\"" + title + "\",\"content\":\"본문\""
                        + (extra.isBlank() ? "" : "," + extra) + "}", token);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return JsonPath.read(r.body(), "$.data");
    }

    private int update(String token, String postId, String title, String extra) throws Exception {
        return post("/api/adm/post/updatePost.do",
                "{\"rowId\":\"" + postId + "\",\"title\":\"" + title + "\",\"content\":\"본문\"," + extra + "}", token)
                .statusCode();
    }

    private int comment(String token, String postId, String content) throws Exception {
        return post("/api/adm/comment/insertComment.do",
                "{\"postId\":\"" + postId + "\",\"content\":\"" + content + "\"}", token).statusCode();
    }

    private HttpResponse<String> commentList(String token, String postId) throws Exception {
        return post("/api/adm/comment/selectCommentList.do", "{\"postId\":\"" + postId + "\"}", token);
    }

    private int map(String token, String postId, String fileId) throws Exception {
        return post("/api/adm/file/saveFileMapping.do",
                "{\"mapKey\":\"" + postId + "\",\"fileType\":\"POST\",\"fileIds\":[\"" + fileId + "\"]}", token).statusCode();
    }

    private int fileList(String token, String postId) throws Exception {
        return post("/api/adm/file/selectFileMapList.do",
                "{\"mapKey\":\"" + postId + "\",\"fileType\":\"POST\"}", token).statusCode();
    }

    private String listTitle(String token, String postId) throws Exception {
        String body = post("/api/adm/post/selectPostList.do",
                "{\"boardId\":\"" + NOTICE_BOARD + "\",\"pageSize\":\"200\"}", token).body();
        List<String> titles = JsonPath.read(body, "$.data.list[?(@.rowId=='" + postId + "')].title");
        assertThat(titles).as("목록에 그 글이 있어야 한다").hasSize(1);
        return titles.get(0);
    }

    private int searchCount(String token, String keyword) throws Exception {
        return JsonPath.read(post("/api/adm/post/selectPostList.do",
                "{\"boardId\":\"" + NOTICE_BOARD + "\",\"filterKeyword\":\"" + keyword + "\"}", token).body(),
                "$.data.totalCount");
    }

    private String column(String postId, String col) {
        return jdbc.queryForObject("SELECT " + col + " FROM post WHERE post_id = ?::integer", String.class,
                Integer.parseInt(postId));
    }

    private String upload(String token, String name) throws Exception {
        String boundary = "ZZBoundary" + System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + name + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(PNG);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/adm/file/upload.do"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(200);
        return JsonPath.read(res.body(), "$.data[0].fileId");
    }
}
