package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 파일 접근제어 — 관리 기능(gc/삭제/목록)은 관리자 전용, 다운로드 잘못된 id는 404, 어디에도 안 붙은 파일은 올린 사람만. */
class FileAccessTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM file WHERE original_name LIKE 'zzacc%'");
    }

    @Test
    @DisplayName("일반회원은 파일 관리 기능(gc/목록/삭제) 호출 시 403")
    void memberBlockedFromFileAdminOps() throws Exception {
        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/file/gc.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/file/selectFileList.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/file/deleteFile.do", "{\"fileId\":\"1\"}", user).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("관리자는 파일 관리 기능 사용 가능(gc/목록 200)")
    void adminAllowedFileAdminOps() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/file/gc.do", "{}", admin).statusCode()).isEqualTo(200);
        assertThat(post("/api/adm/file/selectFileList.do", "{}", admin).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("존재하지 않는 파일 다운로드는 404(NPE 500 아님)")
    void downloadInvalidIdReturns404() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(get("/api/adm/file/download.do?fileId=999999", admin).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("어디에도 안 붙은 파일은 올린 사람만 받는다(다른 회원 403) — 내려받기·공개 이미지 둘 다")
    void unlinkedFileOnlyForUploader() throws Exception {
        // 올리고 아직 저장 안 한 첨부·제출 전 폼 서류·연결이 거절된 첨부가 이 상태로 GC 유예(24시간) 동안 남는다.
        // v0.6.31(CMS 1.2.31)까지는 "편집 중 미리보기"라며 로그인만 보고 통과시켜, file_id 순번만으로 남이 받을 수 있었다
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");
        createMember(admin, "zzacc01");
        String other = accessToken("zzacc01", "Test1234!@");
        String fileId = upload(user, "zzacc-draft.png");

        assertThat(get("/api/adm/file/download.do?fileId=" + fileId, other).statusCode()).isEqualTo(403);
        assertThat(get("/api/pub/image/" + fileId, other).statusCode()).isEqualTo(403);

        assertThat(get("/api/adm/file/download.do?fileId=" + fileId, user).statusCode()).as("올린 사람(미리보기)").isEqualTo(200);
        assertThat(get("/api/pub/image/" + fileId, user).statusCode()).as("에디터 미리보기").isEqualTo(200);
        assertThat(get("/api/pub/image/" + fileId, admin).statusCode()).as("관리자").isEqualTo(200);
        assertThat(get("/api/pub/image/" + fileId, null).statusCode()).as("비로그인").isEqualTo(403);
    }

    @Test
    @DisplayName("게시글 외 용도(폼 첨부 등)의 첨부 목록은 관리자만 — 비로그인·회원 403")
    void nonPostFileMapListIsAdminOnly() throws Exception {
        // 이 API는 비로그인에게도 열려 있다(게시글 첨부 목록 때문). v0.6.32까지 게시글 외 용도는 검사를 안 해,
        // 폼 첨부 서류의 파일명·파일 ID를 응답 번호 순번으로 누구나 받았다. 화면이 부르는 용도는 POST·POST_IMG뿐이다
        String body = "{\"mapKey\":\"1\",\"fileType\":\"FORM\"}";
        assertThat(post("/api/adm/file/selectFileMapList.do", body, null).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/file/selectFileMapList.do", body, accessToken("user", "user1234!")).statusCode())
                .isEqualTo(403);
        assertThat(post("/api/adm/file/selectFileMapList.do", body, accessToken("admin", "admin1234!")).statusCode())
                .isEqualTo(200);
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
