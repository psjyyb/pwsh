package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.configitem.service.ConfigItemService;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 분석 도구(CMS 1.2.44) — GA4 측정 ID·네이버 애널리틱스 사이트 ID(확장설정). 스크립트는 사용자 화면이 방문자 동의 뒤에만 붙인다(프론트).
 *
 * <p>서버가 지키는 것: 두 ID가 비로그인 공개 설정으로 내려가는가(사용자 화면이 읽는다), 저장할 때 형식을 보는가
 * (공개되어 스크립트 주소·전역 변수로 들어가는 값이라 엉뚱한 글자가 섞이면 추적이 조용히 안 되거나 주입 통로가 된다),
 * 형식이 틀리면 함께 보낸 다른 설정도 저장하지 않는가(관리 화면은 모든 항목을 한 번에 보낸다).
 */
class AnalyticsTest extends IntegrationTest {

    private static final String GA = "analytics.ga4-id";
    private static final String NAVER = "analytics.naver-id";
    private static final String FOOTER = "site.footer-text";

    @Autowired
    private ConfigItemService configItemService;

    private String admin;
    private String footerBefore;

    @BeforeEach
    void setUp() throws Exception {
        admin = accessToken("admin", "admin1234!");
        footerBefore = value(FOOTER);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("UPDATE config_item SET value = '' WHERE config_key IN (?, ?)", GA, NAVER);
        jdbc.update("UPDATE config_item SET value = ? WHERE config_key = ?", footerBefore, FOOTER);
        configItemService.evict();
    }

    @Test
    @DisplayName("두 ID는 '분석 도구' 묶음의 공개 설정 — 기본은 비어 있다(추적 꺼짐), 비로그인 공개 목록에 내려간다")
    void seededAsPublicSettings() throws Exception {
        String adm = post("/api/adm/configitem/selectConfigItemList.do", "{}", admin).body();
        List<String> keys = JsonPath.read(adm, "$.data[?(@.groupCd == 'ANALYTICS')].configKey");
        assertThat(keys).containsExactly(GA, NAVER);
        assertThat(value(GA)).isEmpty();
        assertThat(value(NAVER)).isEmpty();

        List<String> pub = JsonPath.read(post("/api/pub/configitem", "{}", null).body(), "$.data[*].configKey");
        assertThat(pub).contains(GA, NAVER);
    }

    @Test
    @DisplayName("맞는 형식은 앞뒤 공백을 떼고 저장해 공개 목록에 그대로 나가고, 빈 값으로 저장하면 끈다")
    void validIdsSavedTrimmed() throws Exception {
        assertThat(save(item(GA, " G-AB12CD34EF "), item(NAVER, " a1b2c3d4e5f6 ")).statusCode()).isEqualTo(200);
        assertThat(value(GA)).isEqualTo("G-AB12CD34EF");
        assertThat(value(NAVER)).isEqualTo("a1b2c3d4e5f6");
        String pub = post("/api/pub/configitem", "{}", null).body();
        assertThat(JsonPath.<List<String>>read(pub, "$.data[?(@.configKey == '" + GA + "')].value")).containsExactly("G-AB12CD34EF");

        // 길이 경계 — GA4는 G- 뒤 4~20자, 네이버는 4~40자
        assertThat(save(item(GA, "G-ABCD"), item(NAVER, "ab_1")).statusCode()).isEqualTo(200);
        assertThat(save(item(GA, "G-" + "A".repeat(20)), item(NAVER, "a".repeat(40))).statusCode()).isEqualTo(200);

        assertThat(save(item(GA, ""), item(NAVER, "  ")).statusCode()).isEqualTo(200);
        assertThat(value(GA)).isEmpty();
        assertThat(value(NAVER)).isEmpty();
    }

    @Test
    @DisplayName("틀린 형식은 400(무엇이 틀렸는지 안내) — 구버전 UA·소문자·따옴표·꺾쇠·공백·길이 초과")
    void invalidIdsRejected() throws Exception {
        for (String bad : List.of("UA-12345-1", "g-ab12cd34ef", "G-AB12\"><script>", "G-AB 12CD", "G-ABC", "G-" + "A".repeat(21), "AB12CD34EF")) {
            HttpResponse<String> r = save(item(GA, bad));
            assertThat(r.statusCode()).as(bad).isEqualTo(400);
            assertThat((String) JsonPath.read(r.body(), "$.error.message")).as(bad).contains("Google Analytics", "G-");
        }
        for (String bad : List.of("a1b2 c3d4", "\"><img src=x>", "abc", "a".repeat(41), "a1b2;alert(1)")) {
            HttpResponse<String> r = save(item(NAVER, bad));
            assertThat(r.statusCode()).as(bad).isEqualTo(400);
            assertThat((String) JsonPath.read(r.body(), "$.error.message")).as(bad).contains("네이버");
        }
        assertThat(value(GA)).isEmpty();
        assertThat(value(NAVER)).isEmpty();
    }

    @Test
    @DisplayName("형식이 틀리면 같이 보낸 다른 설정도 저장하지 않는다 — 화면엔 실패로 뜨는데 일부만 바뀌면 안 된다")
    void invalidRollsBackWholeSave() throws Exception {
        assertThat(save(item(FOOTER, "ZZ바뀐 푸터"), item(GA, "UA-1")).statusCode()).isEqualTo(400);
        configItemService.evict();
        assertThat(value(FOOTER)).isEqualTo(footerBefore);
        assertThat(configItemService.get(FOOTER, "")).isEqualTo(footerBefore);
        // 다른 설정의 값은 형식 검사를 받지 않는다(푸터 문구의 꺾쇠 등은 그대로 저장)
        assertThat(save(item(FOOTER, "<b>ZZ</b> {title}")).statusCode()).isEqualTo(200);
        assertThat(value(FOOTER)).isEqualTo("<b>ZZ</b> {title}");
    }

    // ===== helpers =====

    private HttpResponse<String> save(String... items) throws Exception {
        return post("/api/adm/configitem/updateConfigItem.do", "[" + String.join(",", items) + "]", admin);
    }

    private static String item(String key, String value) {
        return "{\"configKey\":\"" + key + "\",\"value\":\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private String value(String key) {
        return jdbc.queryForObject("SELECT value FROM config_item WHERE config_key = ?", String.class, key);
    }
}
