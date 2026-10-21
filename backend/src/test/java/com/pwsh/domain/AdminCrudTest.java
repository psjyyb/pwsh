package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 CRUD 라운드트립(문자열 PK 도메인) — insert→view→update→delete 전 구간을 실제 DB로 검증.
 * (자동생성 PK 도메인은 별도 배치)
 */
class AdminCrudTest extends IntegrationTest {

    @Test
    @DisplayName("공통코드 CRUD 라운드트립")
    void codeCrud() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        assertThat(post("/api/adm/code/insertCode.do",
                "{\"rowId\":\"ZZTEST01\",\"pCodeId\":\"0\",\"codeName\":\"테스트코드\"}", admin).statusCode())
                .isEqualTo(200);

        HttpResponse<String> view = post("/api/adm/code/selectCodeView.do", "{\"rowId\":\"ZZTEST01\"}", admin);
        assertThat(view.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(view.body(), "$.data.codeName")).isEqualTo("테스트코드");

        assertThat(post("/api/adm/code/updateCode.do",
                "{\"rowId\":\"ZZTEST01\",\"codeName\":\"테스트코드2\"}", admin).statusCode()).isEqualTo(200);
        HttpResponse<String> view2 = post("/api/adm/code/selectCodeView.do", "{\"rowId\":\"ZZTEST01\"}", admin);
        assertThat((String) JsonPath.read(view2.body(), "$.data.codeName")).isEqualTo("테스트코드2");

        assertThat(post("/api/adm/code/deleteCode.do", "{\"rowId\":\"ZZTEST01\"}", admin).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("권한그룹 CRUD 라운드트립")
    void authGroupCrud() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        assertThat(post("/api/adm/authgroup/insertAuthGroup.do",
                "{\"rowId\":\"ZZTESTG\",\"authGroupName\":\"테스트그룹\"}", admin).statusCode()).isEqualTo(200);

        HttpResponse<String> view = post("/api/adm/authgroup/selectAuthGroupView.do", "{\"rowId\":\"ZZTESTG\"}", admin);
        assertThat(view.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(view.body(), "$.data.authGroupName")).isEqualTo("테스트그룹");

        assertThat(post("/api/adm/authgroup/updateAuthGroup.do",
                "{\"rowId\":\"ZZTESTG\",\"authGroupName\":\"테스트그룹2\"}", admin).statusCode()).isEqualTo(200);

        // 그룹-메뉴 권한 저장 — auth INSERT 컬럼이 스키마와 어긋나면(CMS 1.2.20 컬럼 정리 등) 여기서 500이 난다
        String menuId = jdbc.queryForObject("SELECT MIN(menu_id)::text FROM menu WHERE use_yn = 'Y'", String.class);
        assertThat(post("/api/adm/authgroup/updateAuthGroupMenu.do",
                "{\"authGroupId\":\"ZZTESTG\",\"menuIds\":[\"" + menuId + "\"]}", admin).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth WHERE type = 'GRP' AND conn_id = 'ZZTESTG'"
                + " AND menu_id = ?::integer AND menu_yn = 'Y'", Integer.class, menuId)).isEqualTo(1);

        assertThat(post("/api/adm/authgroup/deleteAuthGroup.do", "{\"rowId\":\"ZZTESTG\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth WHERE conn_id = 'ZZTESTG'", Integer.class))
                .as("그룹을 지우면 권한 행도 함께 정리된다").isZero();
    }

    @Test
    @DisplayName("쓰지 않던 컬럼(auth.search_yn·mod_yn, config.menu_version)은 스키마에서 빠져 있다")
    void unusedColumnsDropped() {
        // 남아 있으면 "조회만/수정 가능"을 나눠 저장하는 것처럼 오해하게 된다(실제 판정은 menu_yn뿐)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                + " WHERE table_schema = current_schema()"
                + " AND ((table_name = 'auth' AND column_name IN ('search_yn', 'mod_yn'))"
                + "   OR (table_name = 'config' AND column_name = 'menu_version'))", Integer.class)).isZero();
    }

    @Test
    @DisplayName("입력 검증 — 필수값 누락 시 400")
    void insertValidationFails() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 코드명 누락
        assertThat(post("/api/adm/code/insertCode.do",
                "{\"rowId\":\"ZZTEST02\",\"pCodeId\":\"0\"}", admin).statusCode()).isEqualTo(400);
    }
}
