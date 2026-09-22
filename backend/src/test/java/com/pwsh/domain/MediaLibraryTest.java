package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.file.service.FileService;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 미디어 라이브러리 — 엔티티에 안 붙은 파일을 보관하고, 어디에 쓰이는지 보고, 정리한다.
 *
 * <p>핵심은 <b>GC와의 관계</b>다. 고아 정리는 "매핑 없는 업로드"를 유예시간 뒤 지우는데,
 * 라이브러리는 바로 그런 파일을 모아두는 곳이라 규칙이 충돌한다. 라이브러리 매핑(map_key=0,
 * file_type='LIBRARY')이 그 충돌을 막는 유일한 장치라, 빠지면 올려둔 파일이 <b>다음 새벽에
 * 조용히 사라진다.</b> 화면에는 아무 표시도 나지 않는다.
 */
class MediaLibraryTest extends IntegrationTest {

    /** PNG 매직바이트 — 업로드는 확장자가 아니라 내용으로 형식을 검사한다(FileSignature) */
    private static final byte[] PNG = new byte[] {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R',
    };

    @Autowired
    private FileService fileService;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM post WHERE title LIKE 'ZZ미디어%'");
        // ★ 프로필 참조를 먼저 끊어야 한다 — GC의 A케이스가 profile_file_id로 쓰이는 파일을 제외해서,
        //   남겨두면 테스트 파일이 정리되지 않고 디스크에 쌓인다
        jdbc.update("UPDATE member SET profile_file_id = NULL WHERE profile_file_id IN"
                + " (SELECT file_id FROM file WHERE original_name LIKE 'zzml%')");
        // 매핑을 끊고 업로드 시각을 유예시간 밖으로 민 뒤 GC에 태운다 → DB 행 + 디스크 파일까지 정리된다
        jdbc.update("DELETE FROM file_ref WHERE file_id IN (SELECT file_id FROM file WHERE original_name LIKE 'zzml%')");
        jdbc.update("UPDATE file SET use_yn = 'Y', reg_dt = NOW() - INTERVAL '48 hours'"
                + " WHERE original_name LIKE 'zzml%'");
        fileService.sweep();
    }

    @Test
    @DisplayName("라이브러리에 올린 파일은 아무 데도 안 쓰여도 고아 정리에 지워지지 않는다")
    void libraryFileSurvivesGc() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String libId = uploadLibrary(admin, "zzml-keep.png");
        String plainId = uploadPlain(admin, "zzml-drop.png"); // 대조군: 그냥 올리기만 한 파일

        backdateUpload(libId);
        backdateUpload(plainId);
        fileService.sweep();

        assertThat(exists(libId)).as("라이브러리 파일이 사라지면 미리 올려둘 수가 없다").isTrue();
        assertThat(exists(plainId)).as("매핑 없는 업로드는 유예시간 뒤 정리돼야 한다").isFalse();
    }

    @Test
    @DisplayName("라이브러리 담기/빼기 — 빼면 다시 고아 정리 대상이 된다")
    void toggleLibraryChangesGcFate() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String fileId = uploadPlain(admin, "zzml-toggle.png");

        assertThat(post("/api/adm/file/updateFileLibrary.do",
                "{\"fileId\":\"" + fileId + "\",\"useYn\":\"Y\"}", admin).statusCode()).isEqualTo(200);
        backdateUpload(fileId);
        fileService.sweep();
        assertThat(exists(fileId)).isTrue();

        assertThat(post("/api/adm/file/updateFileLibrary.do",
                "{\"fileId\":\"" + fileId + "\",\"useYn\":\"N\"}", admin).statusCode()).isEqualTo(200);
        backdateUpload(fileId);
        fileService.sweep();
        assertThat(exists(fileId)).as("라이브러리에서 뺐으면 원래 규칙으로 돌아가야 한다").isFalse();
    }

    @Test
    @DisplayName("사용처 조회는 용도와 대상(게시글 제목)을 알려준다")
    void refsShowWhereFileIsUsed() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String fileId = uploadLibrary(admin, "zzml-used.png");
        String postId = newPost(admin, "ZZ미디어 사용처");
        mapToPost(admin, postId, fileId);

        String body = post("/api/adm/file/selectFileListRef.do",
                "{\"fileId\":\"" + fileId + "\"}", admin).body();

        List<String> types = JsonPath.read(body, "$.data[*].fileType");
        assertThat(types).containsExactly("POST_IMG");
        List<String> names = JsonPath.read(body, "$.data[*].targetName");
        assertThat(names).containsExactly("ZZ미디어 사용처");
        // LIBRARY는 "소속" 표시라 사용처로 세지 않는다 — 세면 모든 라이브러리 파일이 '사용 중'이 된다
        assertThat(types).doesNotContain("LIBRARY");
    }

    @Test
    @DisplayName("라이브러리 이미지를 쓴 게시글을 지워도 원본은 살아 있다")
    void deletingPostKeepsLibraryOriginal() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String fileId = uploadLibrary(admin, "zzml-shared.png");
        String postId = newPost(admin, "ZZ미디어 글삭제");
        mapToPost(admin, postId, fileId);

        assertThat(post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + postId + "\"}", admin).statusCode())
                .isEqualTo(200);

        assertThat(jdbc.queryForObject(
                "SELECT use_yn FROM file WHERE file_id = ?::integer", String.class, fileId))
                .as("글 하나 지웠다고 라이브러리 원본이 죽으면 다른 글에서 쓰던 것까지 깨진다")
                .isEqualTo("Y");
    }

    @Test
    @DisplayName("게시글 매핑에서 빼도 라이브러리 원본은 물리삭제되지 않는다")
    void unmappingKeepsLibraryOriginal() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String fileId = uploadLibrary(admin, "zzml-unmap.png");
        String postId = newPost(admin, "ZZ미디어 매핑해제");
        mapToPost(admin, postId, fileId);

        // 매핑을 비우면 saveFileMapping이 "빠진 파일"을 hard-delete 하려 한다
        assertThat(post("/api/adm/file/saveFileMapping.do",
                "{\"mapKey\":\"" + postId + "\",\"fileType\":\"POST_IMG\",\"fileIds\":[]}", admin).statusCode())
                .isEqualTo(200);

        assertThat(exists(fileId)).as("LIBRARY 참조가 남아 있으면 지우면 안 된다").isTrue();
    }

    @Test
    @DisplayName("목록 필터 — 라이브러리만/미사용만 골라 볼 수 있다")
    void listFiltersWork() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String libId = uploadLibrary(admin, "zzml-filter-lib.png");
        String plainId = uploadPlain(admin, "zzml-filter-plain.png");

        List<String> libOnly = JsonPath.read(post("/api/adm/file/selectFileList.do",
                "{\"filterLibrary\":\"Y\",\"filterKeyword\":\"zzml-filter\"}", admin).body(),
                "$.data.list[*].fileId");
        assertThat(libOnly).contains(libId).doesNotContain(plainId);

        // 둘 다 아직 아무 글에도 안 붙었으므로 '미사용'이다(라이브러리 담김 여부와 무관)
        List<String> unused = JsonPath.read(post("/api/adm/file/selectFileList.do",
                "{\"filterUsed\":\"UNUSED\",\"filterKeyword\":\"zzml-filter\"}", admin).body(),
                "$.data.list[*].fileId");
        assertThat(unused).contains(libId, plainId);

        List<String> etc = JsonPath.read(post("/api/adm/file/selectFileList.do",
                "{\"filterExtGroup\":\"ETC\",\"filterKeyword\":\"zzml-filter\"}", admin).body(),
                "$.data.list[*].fileId");
        assertThat(etc).as("png는 이미지라 '이미지 외' 필터에 잡히면 안 된다").isEmpty();
    }

    @Test
    @DisplayName("프로필 사진으로 쓰이는 파일은 '사용 중'으로 잡히고 사용처에 회원이 뜬다")
    void profilePhotoCountsAsUsed() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String fileId = uploadLibrary(admin, "zzml-profile.png");
        // 프로필 사진만 file_ref를 안 거치고 member.profile_file_id로 직접 참조한다(이 저장소의 예외)
        jdbc.update("UPDATE member SET profile_file_id = ?::integer WHERE member_id = 'user'", fileId);

        List<String> types = JsonPath.read(post("/api/adm/file/selectFileListRef.do",
                "{\"fileId\":\"" + fileId + "\"}", admin).body(), "$.data[*].fileType");
        assertThat(types).as("file_ref만 보면 프로필 사진은 사용처가 없는 것처럼 보인다").contains("PROFILE");

        List<String> unused = JsonPath.read(post("/api/adm/file/selectFileList.do",
                "{\"filterUsed\":\"UNUSED\",\"filterKeyword\":\"zzml-profile\"}", admin).body(),
                "$.data.list[*].fileId");
        assertThat(unused).as("'미사용'으로 보이면 관리자가 지워 회원 프로필이 깨진다").doesNotContain(fileId);
    }

    @Test
    @DisplayName("일반회원은 라이브러리 기능을 쓸 수 없다")
    void memberBlockedFromLibraryOps() throws Exception {
        String user = accessToken("user", "user1234!");
        // /api/adm/file/** 는 메뉴권한 검사에서 면제된 경로다 — 컨트롤러가 직접 막지 않으면 다 열린다
        assertThat(post("/api/adm/file/selectFileListRef.do", "{\"fileId\":\"1\"}", user).statusCode())
                .isEqualTo(403);
        assertThat(post("/api/adm/file/updateFileLibrary.do",
                "{\"fileId\":\"1\",\"useYn\":\"Y\"}", user).statusCode()).isEqualTo(403);
        assertThat(uploadMultipart("/api/adm/file/uploadLibrary.do", user, "files", "zzml-deny.png", PNG)
                .statusCode()).isEqualTo(403);
    }

    // ===== helpers =====

    private String uploadLibrary(String token, String name) throws Exception {
        HttpResponse<String> res = uploadMultipart("/api/adm/file/uploadLibrary.do", token, "files", name, PNG);
        assertThat(res.statusCode()).isEqualTo(200);
        String fileId = JsonPath.read(res.body(), "$.data[0].fileId");
        return fileId;
    }

    private String uploadPlain(String token, String name) throws Exception {
        HttpResponse<String> res = uploadMultipart("/api/adm/file/upload.do", token, "files", name, PNG);
        assertThat(res.statusCode()).isEqualTo(200);
        String fileId = JsonPath.read(res.body(), "$.data[0].fileId");
        return fileId;
    }

    private String newPost(String token, String title) throws Exception {
        String postId = JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"" + title + "\",\"content\":\"본문\"}", token).body(), "$.data");
        return postId;
    }

    private void mapToPost(String token, String postId, String fileId) throws Exception {
        assertThat(post("/api/adm/file/saveFileMapping.do",
                "{\"mapKey\":\"" + postId + "\",\"fileType\":\"POST_IMG\",\"fileIds\":[\"" + fileId + "\"]}",
                token).statusCode()).isEqualTo(200);
    }

    /** 업로드 시각을 GC 유예(기본 24시간) 밖으로 민다 */
    private void backdateUpload(String fileId) {
        jdbc.update("UPDATE file SET reg_dt = NOW() - INTERVAL '48 hours' WHERE file_id = ?::integer", fileId);
    }

    private boolean exists(String fileId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM file WHERE file_id = ?::integer", Integer.class, fileId);
        return n != null && n > 0;
    }

    /** multipart/form-data 요청을 손으로 만든다 — java.net.http에는 멀티파트 빌더가 없다 */
    private HttpResponse<String> uploadMultipart(String path, String token, String partName,
                                                 String fileName, byte[] content) throws Exception {
        String boundary = "ZZBoundary" + System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
