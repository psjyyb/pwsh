package com.pwsh;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 하니스 스모크 — 컨텍스트 로딩, 스키마/시드 로딩, 로그인, 익명 GEN 메뉴트리(GUEST 권한)까지
 * 전 구간이 실제 서버+실제 DB로 동작하는지 최소 확인.
 */
class SmokeTest extends IntegrationTest {

    @Test
    @DisplayName("스프링 컨텍스트가 실제 DB로 정상 로딩된다")
    void contextLoads() {
    }

    @Test
    @DisplayName("시드 관리자 계정(admin)이 로그인되어 토큰이 발급된다")
    void adminCanLogin() throws Exception {
        assertThat(accessToken("admin", "admin1234!")).isNotBlank();
    }

    @Test
    @DisplayName("비로그인(익명)도 GEN 메뉴트리를 조회할 수 있다(GUEST 권한 필터)")
    void anonymousCanReadGenMenuTree() throws Exception {
        HttpResponse<String> r = post("/api/adm/menu/selectMenuListTree.do", "{\"area\":\"GEN\"}", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(r.body(), "$.success")).isTrue();
    }
}
