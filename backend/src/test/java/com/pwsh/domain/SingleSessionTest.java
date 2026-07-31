package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** C — token_ver 기반 단일세션(last-wins)·로그아웃 무효화·관리자 강제 로그아웃·stale refresh 차단. */
class SingleSessionTest extends IntegrationTest {

    private static final String PW_EXTEND = "/api/auth/pwExtend";

    @Test
    @DisplayName("같은 계정 재로그인 시 이전 토큰은 무효(last-wins), 새 토큰만 유효")
    void secondLoginInvalidatesFirst() throws Exception {
        String first = accessToken("user", "user1234!");
        String second = accessToken("user", "user1234!");
        assertThat(post(PW_EXTEND, "{}", first).statusCode()).isEqualTo(401);
        assertThat(post(PW_EXTEND, "{}", second).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("관리자가 강제 로그아웃하면 대상 사용자의 토큰이 즉시 무효")
    void adminForceLogoutRevokesUserToken() throws Exception {
        String userToken = accessToken("user", "user1234!");
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/user/updateUserForceLogout.do", "{\"userId\":\"user\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(post(PW_EXTEND, "{}", userToken).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("비관리자는 강제 로그아웃 엔드포인트에 접근 불가(403)")
    void nonAdminCannotForceLogout() throws Exception {
        String userToken = accessToken("user", "user1234!");
        assertThat(post("/api/adm/user/updateUserForceLogout.do", "{\"userId\":\"admin\"}", userToken).statusCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("이전 세션의 refresh 토큰은 재로그인 후 무효(ver 불일치)")
    void staleRefreshRejected() throws Exception {
        String oldRefresh = JsonPath.read(login("user", "user1234!").body(), "$.data.refreshToken");
        accessToken("user", "user1234!"); // 재로그인 → token_ver 증가
        assertThat(post("/api/auth/refresh", "{\"refreshToken\":\"" + oldRefresh + "\"}", null).statusCode())
                .isEqualTo(401);
    }
}
