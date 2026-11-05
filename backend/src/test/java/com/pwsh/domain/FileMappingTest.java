package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게시글 첨부 연결(saveFileMapping) — 남의 파일 차단 + 게시판별 첨부 제한.
 *
 * <p>왜 이 테스트가 있나(CMS 1.2.31 흡수, v0.6.31).
 * <ul>
 *   <li><b>남의 파일</b>: 다운로드 인가(assertServable)는 파일이 붙은 글 중 하나라도 볼 수 있으면 통과시킨다. v0.6.30까지
 *       연결이 "본인이 올린 파일인지"를 안 봐서, 파일 ID(순번)만 알면 남의 서류·비공개 게시판 첨부를 내 공개 글에 붙여
 *       내려받을 수 있었다.</li>
 *   <li><b>첨부 제한</b>: 게시판의 첨부 사용 여부·개수·용량은 화면만 막고 서버는 안 봤다 — 요청을 직접 만들면 넘어간다.</li>
 * </ul>
 */
class FileMappingTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    /** 사용자 화면에 공개된 게시판(기초데이터: 공지사항) — 일반 회원도 글을 쓴다. 취미 게시판은 메뉴 없이 공개라 쓰지 않는다 */
    private static final String PUBLIC_BOARD = "1";

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM file_ref WHERE map_key IN (SELECT post_id FROM post WHERE title LIKE 'ZZ첨부%')"
                + " AND file_type LIKE 'POST%'");
        jdbc.update("DELETE FROM post WHERE title LIKE 'ZZ첨부%'");
        jdbc.update("DELETE FROM board WHERE name LIKE 'ZZ첨부%'");
        jdbc.update("DELETE FROM file WHERE original_name LIKE 'zzmap%'");
    }

    // ===== 남의 파일 =====

    @Test
    @DisplayName("남의 파일을 내 글에 붙일 수 없다 — 붙여서 내려받는 우회를 막는다")
    void cannotAttachOthersFile() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");
        String secret = secretFile(admin);
        assertThat(download(user, secret)).as("전제: 비공개 게시판 첨부는 일반 회원이 못 받는다").isEqualTo(403);

        String myPost = newPost(user, PUBLIC_BOARD);
        int attach = map(user, myPost, "POST", secret);
        assertThat(download(user, secret)).as("내 공개 글에 붙여 내려받는 우회").isEqualTo(403);
        assertThat(attach).isEqualTo(403);
        assertThat(map(user, myPost, "POST_IMG", secret)).isEqualTo(403);
    }

    @Test
    @DisplayName("본문 이미지로 남의 파일을 넣어도 연결되지 않는다(저장은 막지 않는다)")
    void othersFileInEditorIsNotLinked() throws Exception {
        // 본문은 다른 글의 이미지를 복사해 붙여 넣을 수 있어 막으면 정상 저장이 깨진다 → 연결만 하지 않는다
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");
        String secret = secretFile(admin);
        String mine = upload(user, "zzmap-mine.png", 100);

        String myPost = newPost(user, PUBLIC_BOARD);
        assertThat(map(user, myPost, "POST_EDITOR", secret, mine)).isEqualTo(200);
        assertThat(linked(myPost, "POST_EDITOR")).containsExactly(mine);
        assertThat(download(user, secret)).isEqualTo(403);
    }

    @Test
    @DisplayName("내가 올린 파일은 붙이고, 이미 붙어 있던 남의 파일은 다시 저장해도 된다")
    void ownFileAndAlreadyLinkedFileAreKept() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");
        String myPost = newPost(user, PUBLIC_BOARD);
        String adminFile = upload(admin, "zzmap-admin.png", 100);
        assertThat(map(admin, myPost, "POST", adminFile)).as("관리자는 예외").isEqualTo(200);

        String mine = upload(user, "zzmap-mine.png", 100);
        // 수정 화면은 기존 첨부를 그대로 다시 보낸다 — 이미 붙어 있던 것까지 막으면 글 수정이 깨진다
        assertThat(map(user, myPost, "POST", adminFile, mine)).isEqualTo(200);
        assertThat(linked(myPost, "POST")).containsExactly(adminFile, mine);
    }

    @Test
    @DisplayName("숫자가 아닌 파일 ID는 400")
    void nonNumericFileIdIsRejected() throws Exception {
        String user = accessToken("user", "user1234!");
        assertThat(map(user, newPost(user, PUBLIC_BOARD), "POST", "abc")).isEqualTo(400);
    }

    // ===== 게시판별 첨부 제한 (관리자도 적용) =====

    @Test
    @DisplayName("첨부를 쓰지 않는 게시판엔 첨부를 붙일 수 없다(관리자도)")
    void attachDisabledBoard() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String post = newPost(admin, newBoard("ZZ첨부안씀", "N", 5, 10));
        assertThat(map(admin, post, "POST", upload(admin, "zzmap-a.png", 100))).isEqualTo(400);
    }

    @Test
    @DisplayName("첨부 개수 제한을 넘으면 400, 이내면 200")
    void countLimit() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String post = newPost(admin, newBoard("ZZ첨부개수", "Y", 2, 10));
        String a = upload(admin, "zzmap-a.png", 100);
        String b = upload(admin, "zzmap-b.png", 100);
        String c = upload(admin, "zzmap-c.png", 100);

        assertThat(map(admin, post, "POST", a, b, c)).isEqualTo(400);
        assertThat(map(admin, post, "POST", a, b)).isEqualTo(200);
    }

    @Test
    @DisplayName("첨부 용량 제한을 넘는 파일은 400(갤러리 사진도)")
    void sizeLimit() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String post = newPost(admin, newBoard("ZZ첨부용량", "Y", 5, 1));
        String big = upload(admin, "zzmap-big.png", 1024 * 1024 + 1);
        String small = upload(admin, "zzmap-small.png", 1024 * 1024);

        assertThat(map(admin, post, "POST", big)).isEqualTo(400);
        assertThat(map(admin, post, "POST_IMG", big)).isEqualTo(400);
        assertThat(map(admin, post, "POST", small)).as("경계값(정확히 1MB)은 통과").isEqualTo(200);
    }

    @Test
    @DisplayName("제한을 줄여도 기존 첨부는 다시 저장된다 — 새로 붙일 때만 검사")
    void limitAppliesOnlyWhenAdding() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String boardId = newBoard("ZZ첨부축소", "Y", 3, 10);
        String post = newPost(admin, boardId);
        String a = upload(admin, "zzmap-a.png", 100);
        String b = upload(admin, "zzmap-b.png", 100);
        String c = upload(admin, "zzmap-c.png", 100);
        assertThat(map(admin, post, "POST", a, b, c)).isEqualTo(200);

        jdbc.update("UPDATE board SET file_cnt_limit = 1 WHERE board_id = ?::integer", boardId);
        assertThat(map(admin, post, "POST", a, b, c)).as("제목·본문만 고친 수정 저장이 막히면 안 된다").isEqualTo(200);
        assertThat(map(admin, post, "POST", a, b, c, upload(admin, "zzmap-d.png", 100))).isEqualTo(400);
    }

    @Test
    @DisplayName("갤러리 사진은 첨부 개수 제한을 받지 않는다(화면과 같다)")
    void galleryIgnoresCountLimit() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String post = newPost(admin, newBoard("ZZ첨부갤러리", "Y", 1, 10));
        assertThat(map(admin, post, "POST_IMG",
                upload(admin, "zzmap-g1.png", 100), upload(admin, "zzmap-g2.png", 100))).isEqualTo(200);
    }

    // ===== helpers =====

    /** 비공개 게시판(GEN 메뉴 없음) 글에 관리자가 붙인 파일 — 일반 회원은 열람 불가 */
    private String secretFile(String admin) throws Exception {
        String post = newPost(admin, newBoard("ZZ첨부비공개", "Y", 5, 10));
        String fileId = upload(admin, "zzmap-secret.png", 100);
        assertThat(map(admin, post, "POST", fileId)).isEqualTo(200);
        return fileId;
    }

    private String newBoard(String name, String fileYn, int cnt, int sizeMb) {
        jdbc.update("INSERT INTO board (name, type_cd, description, list_cnt, file_yn, file_cnt_limit,"
                + " file_size_limit_mb, notice_yn, new_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (?, 'BOARD01', '첨부 테스트', 10, ?, ?, ?, 'N', 0, 'Y',"
                + " 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", name, fileYn, cnt, sizeMb);
        return jdbc.queryForObject("SELECT board_id::text FROM board WHERE name = ?", String.class, name);
    }

    private String newPost(String token, String boardId) throws Exception {
        HttpResponse<String> r = post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + boardId + "\",\"title\":\"ZZ첨부 글\",\"content\":\"본문\"}", token);
        assertThat(r.statusCode()).isEqualTo(200);
        return JsonPath.read(r.body(), "$.data");
    }

    private int map(String token, String postId, String fileType, String... fileIds) throws Exception {
        String ids = Arrays.stream(fileIds).map(f -> "\"" + f + "\"").collect(Collectors.joining(",", "[", "]"));
        return post("/api/adm/file/saveFileMapping.do",
                "{\"mapKey\":\"" + postId + "\",\"fileType\":\"" + fileType + "\",\"fileIds\":" + ids + "}", token)
                .statusCode();
    }

    private java.util.List<String> linked(String postId, String fileType) {
        return jdbc.queryForList("SELECT file_id::text FROM file_ref WHERE map_key = ?::integer AND file_type = ?"
                + " ORDER BY sort_no", String.class, postId, fileType);
    }

    private int download(String token, String fileId) throws Exception {
        return get("/api/adm/file/download.do?fileId=" + fileId, token).statusCode();
    }

    /** PNG 시그니처 + 0 채움으로 원하는 크기(바이트)의 파일을 올린다(업로드는 시그니처만 본다) */
    private String upload(String token, String name, int size) throws Exception {
        byte[] body = new byte[Math.max(size, PNG.length)];
        System.arraycopy(PNG, 0, body, 0, PNG.length);
        String boundary = "ZZBoundary" + System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + name + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(body);
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
