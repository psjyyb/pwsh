package com.pwsh.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.domain.configitem.service.ConfigItemService;
import com.pwsh.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 확장 설정(키-값).
 * - 정의를 넣으면 관리자 목록에 그룹·순서대로 나오는지(화면 자동 렌더의 근거)
 * - 값 저장이 반영되고 캐시가 즉시 갱신되는지
 * - ★ 공개 조회가 public_yn='Y'만 내려주는지(내부용 설정 유출 방지)
 * - 정의 없는 키 저장은 거부되는지(조용히 무시하면 저장된 줄 알게 된다)
 * - 타입 변환 실패 시 기본값으로 떨어지는지
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class ConfigItemTest extends IntegrationTest {

    @Autowired
    private ConfigItemService configItemService;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM config_item WHERE config_key LIKE 'test.%'");
        configItemService.evict();
    }

    /** 테스트용 항목 정의를 직접 심는다 — 정의는 개발자가 data.sql로 넣는 것이라 API가 없다. */
    private void seed(String key, String value, String inputType, String groupCd, int sortNo, String publicYn) {
        jdbc.update("INSERT INTO config_item (config_key, value, input_type, name, description, group_cd, sort_no,"
                        + " public_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                        + " VALUES (?, ?, ?, ?, '설명', ?, ?, ?, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')",
                key, value, inputType, "테스트-" + key, groupCd, sortNo, publicYn);
        configItemService.evict();
    }

    @Test
    void admin_list_returns_definition_in_group_and_sort_order() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        seed("test.b", "1", "NUMBER", "ZTEST", 2, "N");
        seed("test.a", "hello", "TEXT", "ZTEST", 1, "N");

        String body = post("/api/adm/configitem/selectConfigItemList.do", "{}", admin).body();
        List<String> keys = JsonPath.read(body, "$.data[?(@.groupCd == 'ZTEST')].configKey");
        // 화면이 정렬을 하지 않으므로 서버가 group_cd → sort_no 순으로 줘야 한다
        assertEquals(List.of("test.a", "test.b"), keys);
        // 입력유형·항목명이 함께 내려와야 화면이 입력칸을 만들 수 있다
        List<String> types = JsonPath.read(body, "$.data[?(@.configKey == 'test.b')].inputType");
        assertEquals(List.of("NUMBER"), types);
    }

    @Test
    void value_update_is_applied_and_cache_refreshed() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        seed("test.num", "10", "NUMBER", "ZTEST", 1, "N");
        assertEquals(10, configItemService.getInt("test.num", 99));

        assertEquals(200, post("/api/adm/configitem/updateConfigItem.do",
                "[{\"configKey\":\"test.num\",\"value\":\"42\"}]", admin).statusCode());

        // 저장 직후 캐시가 비워져야 한다 — 안 그러면 최대 60초간 옛 값으로 동작한다
        assertEquals(42, configItemService.getInt("test.num", 99));
        assertEquals("42", jdbc.queryForObject(
                "SELECT value FROM config_item WHERE config_key = 'test.num'", String.class));
    }

    @Test
    void public_query_exposes_only_public_items() throws Exception {
        seed("test.pub", "공개값", "TEXT", "ZTEST", 1, "Y");
        seed("test.private", "내부값", "TEXT", "ZTEST", 2, "N");

        // 비로그인으로 호출
        var res = post("/api/pub/configitem", "{}", null);
        assertEquals(200, res.statusCode());
        String body = res.body();
        assertTrue(body.contains("test.pub"), "공개 항목은 내려가야 한다");
        assertFalse(body.contains("test.private"), "비공개 항목이 새어나가면 안 된다");
        assertFalse(body.contains("내부값"), "비공개 값이 새어나가면 안 된다");
        // 정의 메타(설명·그룹)는 공개할 이유가 없다
        assertFalse(body.contains("groupCd\":\"ZTEST"), "공개 응답에 그룹 메타가 포함되면 안 된다");
    }

    @Test
    void unknown_key_is_rejected() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 정의가 없는 키
        assertEquals(400, post("/api/adm/configitem/updateConfigItem.do",
                "[{\"configKey\":\"test.nonexistent\",\"value\":\"x\"}]", admin).statusCode());
        // 정의가 있어도 미사용(use_yn='N')이면 거부 — 화면에 없는 항목이 저장되면 안 된다
        seed("test.off", "v", "TEXT", "ZTEST", 1, "N");
        jdbc.update("UPDATE config_item SET use_yn = 'N' WHERE config_key = 'test.off'");
        configItemService.evict();
        assertEquals(400, post("/api/adm/configitem/updateConfigItem.do",
                "[{\"configKey\":\"test.off\",\"value\":\"x\"}]", admin).statusCode());
    }

    @Test
    void type_conversion_falls_back_to_default() {
        // 값이 전부 문자열이라 숫자 항목에 글자가 들어갈 수 있다.
        // 그때 예외로 기능을 죽이지 않고 기본값으로 도는 것이 이 설계의 전제다.
        seed("test.broken", "숫자아님", "NUMBER", "ZTEST", 1, "N");
        assertEquals(7, configItemService.getInt("test.broken", 7));

        // 미정의 키도 기본값
        assertEquals("fallback", configItemService.get("test.missing", "fallback"));
        assertTrue(configItemService.getBool("test.missing", true));
        // 빈 값은 미설정으로 본다
        seed("test.empty", "", "TEXT", "ZTEST", 1, "N");
        assertEquals("dflt", configItemService.get("test.empty", "dflt"));
    }

    @Test
    void seeded_public_items_are_readable_without_login() throws Exception {
        // 기초데이터로 넣은 실제 항목이 비로그인에게 내려가야 사용자 화면(푸터·메인)이 그린다
        var res = post("/api/pub/configitem", "{}", null);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("site.footer-text"),
                "기초데이터의 공개 항목이 안 내려오면 사용자 사이트 푸터가 기본 문구로만 나온다");
        assertTrue(res.body().contains("main.news-board-id"),
                "메인 소식 게시판 설정이 안 내려오면 메인의 소식 섹션이 사라진다");
    }

    @Test
    void seeded_news_board_is_readable_without_login() throws Exception {
        // ★ 메인 소식 섹션은 비로그인에게 보여야 한다. 시드 게시판이 비회원에게 403이면
        //    섹션이 조용히 사라지므로(설계상 의도된 동작) 시드값 자체를 검증한다.
        String boardId = jdbc.queryForObject(
                "SELECT value FROM config_item WHERE config_key = 'main.news-board-id'", String.class);
        var res = post("/api/adm/post/selectPostList.do", "{\"boardId\":\"" + boardId + "\"}", null);
        assertEquals(200, res.statusCode(),
                "시드 소식 게시판(" + boardId + ")이 비로그인에게 열려 있지 않다 → 메인 소식 섹션이 안 보인다");
    }
}
