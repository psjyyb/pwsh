package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 인증 — 로그인 성공/실패, 반복 실패 잠금, refresh 토큰 타입 검증, 로그아웃 무효화. */
class AuthTest extends IntegrationTest {

    private static final String PW_EXTEND = "/api/auth/pwExtend"; // 인증 필요 엔드포인트(200/401 판별용)

    @Test
    @DisplayName("올바른 자격으로 로그인하면 200 + 토큰 발급")
    void loginSuccess() throws Exception {
        assertThat(login("admin", "admin1234!").statusCode()).isEqualTo(200);
        assertThat(accessToken("admin", "admin1234!")).isNotBlank();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401")
    void loginWrongPassword() throws Exception {
        assertThat(login("admin", "wrong-pw").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("실패 제한(5회) 초과 시 계정이 잠겨 올바른 비번으로도 로그인 불가")
    void lockoutAfterRepeatedFailures() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(createMember(admin, "locktest").statusCode()).isEqualTo(200);
        for (int i = 0; i < 5; i++) {
            login("locktest", "nope");
        }
        // 잠금 후에는 올바른 비밀번호(Test1234!@)로도 401
        assertThat(login("locktest", "Test1234!@").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("access 토큰을 refresh 자리에 넣으면 재발급 거부(401)")
    void refreshRejectsAccessToken() throws Exception {
        String access = accessToken("admin", "admin1234!");
        assertThat(post("/api/auth/refresh", "{\"refreshToken\":\"" + access + "\"}", null).statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("로그아웃하면 해당 토큰이 즉시 무효화된다")
    void logoutRevokesToken() throws Exception {
        String token = accessToken("user", "user1234!");
        assertThat(post(PW_EXTEND, "{}", token).statusCode()).isEqualTo(200);
        assertThat(post("/api/auth/logout", "{}", token).statusCode()).isEqualTo(200);
        assertThat(post(PW_EXTEND, "{}", token).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("정상 refresh 토큰으로 새 access가 발급된다")
    void refreshIssuesNewAccess() throws Exception {
        String refresh = JsonPath.read(login("user", "user1234!").body(), "$.data.refreshToken");
        var r = post("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(r.body(), "$.data.accessToken")).isNotBlank();
    }
}
