package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 메뉴 아이콘(menu.icon) — 등록 시 저장되고 트리 조회에 반영된다. */
class MenuIconTest extends IntegrationTest {

    @Test
    @DisplayName("아이콘을 지정해 메뉴를 등록하면 트리에 icon으로 반영된다")
    void menuIconPersistsAndReturnsInTree() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/menu/insertMenu.do",
                "{\"menuName\":\"아이콘테스트\",\"area\":\"ADM\",\"connCd\":\"MENU01\",\"linkUrl\":\"/adm/xicontest\",\"icon\":\"star\"}",
                admin).statusCode()).isEqualTo(200);

        HttpResponse<String> tree = post("/api/adm/menu/selectMenuListManageTree.do", "{\"area\":\"ADM\"}", admin);
        assertThat(tree.statusCode()).isEqualTo(200);
        List<String> icons = JsonPath.read(tree.body(), "$..icon");
        assertThat(icons).contains("star");

        // 정리: 등록한 테스트 메뉴 삭제
        List<String> ids = JsonPath.read(tree.body(), "$.data[?(@.menuName=='아이콘테스트')].rowId");
        for (String id : ids) {
            post("/api/adm/menu/deleteMenu.do", "{\"rowId\":\"" + id + "\"}", admin);
        }
    }

    /**
     * ★ 기초데이터가 메뉴에 넣는 아이콘 키는 <b>프론트 레지스트리에 실제로 있는 키</b>여야 한다.
     * 없는 키를 넣으면 예외도 경고도 없이 기본 아이콘(grid)으로 조용히 표시돼 한참 모른다.
     *
     * <p>아래 목록은 {@code frontend/src/common/adm/components/MenuGlyph.tsx}의 키와 같아야 한다.
     * data.sql의 아이콘 CASE에 새 키를 추가하면 이 테스트가 먼저 실패한다 →
     * 그때 MenuGlyph에 아이콘을 추가하고 이 목록에도 넣는다.
     */
    private static final List<String> REGISTERED_ICON_KEYS = List.of(
            "grid", "home", "user", "group", "list", "code", "board", "page", "popup", "policy",
            "file", "log", "setting", "chart", "bell", "mail", "calendar", "search", "lock", "key",
            "shield", "star", "bookmark", "tag", "image", "download", "upload", "link", "edit",
            "trash", "check", "info", "help", "database", "globe", "phone", "chat", "eye",
            "clipboard", "won", "filter", "box", "flag", "location", "clock",
            "book", "printer", "warning", "close", "plus", "minus", "refresh", "sliders",
            "send", "share", "heart", "award", "school", "video", "camera", "music",
            "cart", "card", "wallet", "ticket", "gift", "truck", "building", "store", "map",
            "trend", "pie");

    @Test
    @DisplayName("기초데이터가 부여한 메뉴 아이콘 키는 모두 프론트 레지스트리에 있는 키다")
    void seededIconsExistInRegistry() {
        List<String> used = jdbc.queryForList(
                "SELECT DISTINCT icon FROM menu WHERE icon IS NOT NULL AND icon <> ''", String.class);
        assertThat(used).isNotEmpty(); // 시드가 아이콘을 하나도 안 넣었다면 이 테스트가 무의미해진다
        assertThat(used)
                .as("MenuGlyph에 없는 아이콘 키는 기본 아이콘으로 표시된다 — MenuGlyph.tsx에 추가하고 이 목록에도 넣을 것")
                .allMatch(REGISTERED_ICON_KEYS::contains);
    }
}
