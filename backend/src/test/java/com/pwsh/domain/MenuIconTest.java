package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 메뉴 아이콘(t_menu.icon) — 등록 시 저장되고 트리 조회에 반영된다. */
class MenuIconTest extends IntegrationTest {

    @Test
    @DisplayName("아이콘을 지정해 메뉴를 등록하면 트리에 icon으로 반영된다")
    void menuIconPersistsAndReturnsInTree() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/menu/insertMenu.do",
                "{\"menuNm\":\"아이콘테스트\",\"area\":\"ADM\",\"connTy\":\"MENU01\",\"linkUrl\":\"/adm/xicontest\",\"icon\":\"star\"}",
                admin).statusCode()).isEqualTo(200);

        HttpResponse<String> tree = post("/api/adm/menu/selectMenuListManageTree.do", "{\"area\":\"ADM\"}", admin);
        assertThat(tree.statusCode()).isEqualTo(200);
        List<String> icons = JsonPath.read(tree.body(), "$..icon");
        assertThat(icons).contains("star");

        // 정리: 등록한 테스트 메뉴 삭제
        List<String> ids = JsonPath.read(tree.body(), "$.data[?(@.menuNm=='아이콘테스트')].dbKey");
        for (String id : ids) {
            post("/api/adm/menu/deleteMenu.do", "{\"dbKey\":\"" + id + "\"}", admin);
        }
    }
}
