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
                "{\"dbKey\":\"ZZTEST01\",\"pCodeId\":\"0\",\"codeNm\":\"테스트코드\"}", admin).statusCode())
                .isEqualTo(200);

        HttpResponse<String> view = post("/api/adm/code/selectCodeView.do", "{\"dbKey\":\"ZZTEST01\"}", admin);
        assertThat(view.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(view.body(), "$.data.codeNm")).isEqualTo("테스트코드");

        assertThat(post("/api/adm/code/updateCode.do",
                "{\"dbKey\":\"ZZTEST01\",\"codeNm\":\"테스트코드2\"}", admin).statusCode()).isEqualTo(200);
        HttpResponse<String> view2 = post("/api/adm/code/selectCodeView.do", "{\"dbKey\":\"ZZTEST01\"}", admin);
        assertThat((String) JsonPath.read(view2.body(), "$.data.codeNm")).isEqualTo("테스트코드2");

        assertThat(post("/api/adm/code/deleteCode.do", "{\"dbKey\":\"ZZTEST01\"}", admin).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("권한그룹 CRUD 라운드트립")
    void authgrpCrud() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        assertThat(post("/api/adm/authgrp/insertAuthgrp.do",
                "{\"dbKey\":\"ZZTESTG\",\"authgrpNm\":\"테스트그룹\"}", admin).statusCode()).isEqualTo(200);

        HttpResponse<String> view = post("/api/adm/authgrp/selectAuthgrpView.do", "{\"dbKey\":\"ZZTESTG\"}", admin);
        assertThat(view.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(view.body(), "$.data.authgrpNm")).isEqualTo("테스트그룹");

        assertThat(post("/api/adm/authgrp/updateAuthgrp.do",
                "{\"dbKey\":\"ZZTESTG\",\"authgrpNm\":\"테스트그룹2\"}", admin).statusCode()).isEqualTo(200);

        assertThat(post("/api/adm/authgrp/deleteAuthgrp.do", "{\"dbKey\":\"ZZTESTG\"}", admin).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("입력 검증 — 필수값 누락 시 400")
    void insertValidationFails() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 코드명 누락
        assertThat(post("/api/adm/code/insertCode.do",
                "{\"dbKey\":\"ZZTEST02\",\"pCodeId\":\"0\"}", admin).statusCode()).isEqualTo(400);
    }
}
