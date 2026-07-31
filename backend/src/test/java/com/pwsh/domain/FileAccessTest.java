package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 파일 접근제어 — 관리 기능(gc/삭제/목록)은 관리자 전용, 다운로드 잘못된 id는 404. */
class FileAccessTest extends IntegrationTest {

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
}
