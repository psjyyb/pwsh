package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색 통계 · 인기 검색어(CMS 1.2.38) — 통합검색을 할 때마다 날짜·검색어별 횟수만 쌓는다(누가 검색했는지는 남기지 않는다).
 *
 * <p>조용히 틀릴 수 있는 곳 — 대소문자·공백만 다른 검색어가 따로 세지는 것,
 * 인기 검색어에 금칙어·숨긴 말·결과가 한 번도 없던 말·한두 번 들어온 말(누군가의 이름·번호일 수 있다)이 노출되는 것,
 * 그리고 통계(무엇을 검색하는지)를 관리자가 아닌 사람이 보는 것.
 */
class SearchStatTest extends IntegrationTest {

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = accessToken("admin", "admin1234!");
        jdbc.update("DELETE FROM search_keyword_daily");
        jdbc.update("DELETE FROM search_keyword_hide");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM search_keyword_daily");
        jdbc.update("DELETE FROM search_keyword_hide");
        jdbc.update("DELETE FROM banword WHERE word LIKE 'zz%'");
        jdbc.update("DELETE FROM auth WHERE conn_id = 'MEMBER' AND menu_id IN (SELECT menu_id FROM menu WHERE link_url = '/adm/searchstat')");
    }

    @Test
    @DisplayName("통합검색 한 번 = 1회 — 공백·대소문자는 하나로 보고, 결과가 없던 횟수를 따로 센다. 빈 검색어·100자 초과는 세지 않는다")
    void searchIsCountedOncePerDay() throws Exception {
        search("  ZZ   없는말  ");
        search("zz 없는말");
        search("   ");
        search("가".repeat(101));

        Map<String, Object> row = jdbc.queryForMap("SELECT stat_date, search_cnt, zero_cnt FROM search_keyword_daily WHERE keyword = 'zz 없는말'");
        assertThat(((Number) row.get("search_cnt")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("zero_cnt")).intValue()).isEqualTo(2);
        assertThat(row.get("stat_date").toString()).isEqualTo(jdbc.queryForObject("SELECT CURRENT_DATE::text", String.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM search_keyword_daily", Integer.class)).as("빈 검색어·100자 초과는 안 남는다").isEqualTo(1);

        // 결과가 있으면 zero_cnt는 그대로 — 취미·모집·게시글·페이지 네 유형 합으로 본다(여기선 취미 이름)
        assertThat(post("/api/adm/hobby/insertHobby.do", "{\"hobbyName\":\"ZZ통계취미\",\"summary\":\"s\"}", admin).statusCode())
                .isEqualTo(200);
        search("zz통계취미");
        assertThat(jdbc.queryForMap("SELECT search_cnt, zero_cnt FROM search_keyword_daily WHERE keyword = 'zz통계취미'"))
                .containsEntry("search_cnt", 1).containsEntry("zero_cnt", 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'search_keyword_daily'"
                + " AND column_name IN ('member_id', 'reg_ip', 'reg_id')", Integer.class))
                .as("누가 검색했는지는 남기지 않는다").isZero();
    }
    @Test
    @DisplayName("인기 검색어(비로그인 공개): 최근 7일 2회 이상·결과가 있던 말만 많은 순 — 금칙어가 든 말·숨긴 말·오래된 말은 빠진다")
    void popularKeywords() throws Exception {
        seed("zz인기a", 0, 5, 0);
        seed("zz인기a", 2, 2, 0);   // 날짜를 합친다 → 7
        seed("zz인기b", 1, 3, 0);
        seed("zz인기c", 0, 1, 0);   // 한 번뿐(누군가의 이름·번호일 수 있다)
        seed("zz인기d", 0, 4, 4);   // 결과가 한 번도 없었다
        seed("zz인기e", 10, 9, 0);  // 7일 전보다 오래됐다
        seed("zz광고문의", 0, 6, 0);  // 금칙어 'zz광고' 포함
        seed("zz숨김", 0, 8, 0);
        assertThat(post("/api/adm/banword/insertBanword.do", "{\"word\":\"ZZ 광고\"}", admin).statusCode()).isEqualTo(200);
        assertThat(post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\"zz숨김\",\"hideYn\":\"Y\"}", admin).statusCode())
                .isEqualTo(200);

        String body = post("/api/adm/search/selectSearchListPopular.do", "{}", null).body();
        assertThat(JsonPath.<List<String>>read(body, "$.data")).containsExactly("zz인기a", "zz인기b");
        assertThat(body).as("공개 응답엔 횟수를 싣지 않는다").doesNotContain("searchCnt");
    }

    @Test
    @DisplayName("검색 통계(관리자): 기간 안 검색어 순위·결과 없던 검색어·일별 합계 — 금칙어 포함·숨김 여부가 함께 보인다")
    void adminStats() throws Exception {
        seed("zz통계a", 0, 5, 1);
        seed("zz통계a", 1, 2, 0);
        seed("zz통계b", 0, 3, 3);
        seed("zz광고통계", 0, 1, 0);
        seed("zz옛날", 40, 9, 0);
        post("/api/adm/banword/insertBanword.do", "{\"word\":\"zz광고\"}", admin);
        post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\"zz통계b\",\"hideYn\":\"Y\"}", admin);

        String list = post("/api/adm/searchstat/selectSearchStatList.do", "{\"days\":\"7\",\"pageNo\":1,\"pageSize\":10}", admin).body();
        assertThat(JsonPath.<List<String>>read(list, "$.data.list[*].keyword")).containsExactly("zz통계a", "zz통계b", "zz광고통계");
        assertThat(JsonPath.<Integer>read(list, "$.data.totalCount")).isEqualTo(3);
        Map<String, Object> a = row(list, "zz통계a");
        assertThat(a.get("searchCnt")).isEqualTo("7");
        assertThat(a.get("zeroCnt")).isEqualTo("1");
        assertThat(row(list, "zz통계b").get("hiddenYn")).isEqualTo("Y");
        assertThat(row(list, "zz광고통계").get("banYn")).isEqualTo("Y");
        assertThat(a.get("banYn")).isEqualTo("N");

        String zero = post("/api/adm/searchstat/selectSearchStatList.do",
                "{\"days\":\"7\",\"zeroOnly\":\"Y\",\"pageNo\":1,\"pageSize\":10}", admin).body();
        assertThat(JsonPath.<List<String>>read(zero, "$.data.list[*].keyword")).as("결과 없던 횟수 많은 순")
                .containsExactly("zz통계b", "zz통계a");

        String filtered = post("/api/adm/searchstat/selectSearchStatList.do",
                "{\"days\":\"90\",\"filterKeyword\":\"옛\",\"pageNo\":1,\"pageSize\":10}", admin).body();
        assertThat(JsonPath.<List<String>>read(filtered, "$.data.list[*].keyword")).containsExactly("zz옛날");

        String daily = post("/api/adm/searchstat/selectSearchStatListDaily.do", "{\"days\":\"7\"}", admin).body();
        List<Map<String, Object>> days = JsonPath.read(daily, "$.data.list");
        assertThat(days).hasSize(7).as("오늘부터 거꾸로 빈 날도 0으로");
        assertThat(days.get(0).get("searchCnt")).as("오늘 합계 5+3+1").isEqualTo("9");
        assertThat(days.get(0).get("keywordCnt")).isEqualTo("3");
        assertThat(days.get(1).get("searchCnt")).isEqualTo("2");
        assertThat(days.get(2).get("searchCnt")).isEqualTo("0");
    }

    @Test
    @DisplayName("통계는 관리자 화면 권한이 있어야 본다 — 비로그인 401, 일반 회원 403(숨김도)")
    void statsNeedAdminMenu() throws Exception {
        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/searchstat/selectSearchStatList.do", "{}", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/adm/searchstat/selectSearchStatList.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/searchstat/selectSearchStatListDaily.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\"zz\",\"hideYn\":\"Y\"}", user).statusCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("숨김을 풀면 다시 인기 검색어에 나온다 — 숨김은 공백·대소문자를 정리한 검색어 기준")
    void hideAndUnhide() throws Exception {
        seed("zz숨김2", 0, 5, 0);
        assertThat(post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\" ZZ숨김2 \",\"hideYn\":\"Y\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(popular()).as("공백·대소문자가 달라도 같은 말로 숨긴다").doesNotContain("zz숨김2");
        assertThat(post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\"zz숨김2\",\"hideYn\":\"Y\"}", admin).statusCode())
                .as("이미 숨긴 말을 또 숨겨도 된다").isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM search_keyword_hide WHERE use_yn = 'Y'", Integer.class))
                .as("같은 말이라 한 줄").isEqualTo(1);
        assertThat(popular()).doesNotContain("zz숨김2");
        assertThat(post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\"zz숨김2\",\"hideYn\":\"N\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(popular()).contains("zz숨김2");
        assertThat(post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\"  \",\"hideYn\":\"Y\"}", admin).statusCode())
                .isEqualTo(400);
    }

    // ===== helpers =====

    /** 이 저장소의 통합검색(취미·모집·게시글·페이지) — 비로그인으로 */
    private void search(String keyword) throws Exception {
        post("/api/adm/search/selectSearchAll.do", "{\"filterKeyword\":\"" + keyword + "\"}", null);
    }
    /** daysAgo일 전 날짜로 집계 행을 심는다 */
    private void seed(String keyword, int daysAgo, int searchCnt, int zeroCnt) {
        jdbc.update("INSERT INTO search_keyword_daily (stat_date, keyword, search_cnt, zero_cnt)"
                + " VALUES (CURRENT_DATE - ?::integer, ?, ?, ?)", daysAgo, keyword, searchCnt, zeroCnt);
    }

    private List<String> popular() throws Exception {
        return JsonPath.read(post("/api/adm/search/selectSearchListPopular.do", "{}", null).body(), "$.data");
    }

    private Map<String, Object> row(String listBody, String keyword) {
        List<Map<String, Object>> rows = JsonPath.read(listBody, "$.data.list[?(@.keyword=='" + keyword + "')]");
        assertThat(rows).as(keyword).hasSize(1);
        return rows.get(0);
    }
}
