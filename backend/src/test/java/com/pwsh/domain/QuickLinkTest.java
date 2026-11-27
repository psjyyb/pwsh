package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 자주찾는 서비스(CMS 1.2.45) — 사용자 메인 히어로 아래의 바로가기 버튼. 관리자가 이름·주소·아이콘·열기 방식·노출을 정하고 ▲▼로 순서를 바꾼다.
 *
 * <p>조용히 틀릴 수 있는 곳 — ① 메인은 비로그인이 처음 보는 화면이라 공개 경로가 빠지면 메인이 통째로 401,
 * ② 주소가 그대로 링크(href)가 되므로 {@code javascript:}·{@code //다른사이트}가 들어가면 방문자 화면의 스크립트·피싱 통로,
 * ③ 숨김(노출 N)은 지운 게 아니라 순서 자리를 지켜야 한다(다시 켜면 원래 자리), ④ 공개 목록에 관리용 값(등록자 등)이 실리지 않아야 한다.
 */
class QuickLinkTest extends IntegrationTest {

    private static final String BASE = "/api/adm/quicklink/";
    private static final Configuration LENIENT = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    private String admin;
    private List<Map<String, Object>> before;

    @BeforeEach
    void setUp() throws Exception {
        admin = accessToken("admin", "admin1234!");
        // 기초데이터(바로가기 예시)를 테스트가 건드려도 원래대로 돌린다 — 기대값을 써 넣지 않고 그대로 되살린다
        before = jdbc.queryForList("SELECT quick_link_id, sort_no, display_yn, use_yn FROM quick_link");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM quick_link WHERE name LIKE 'ZZ%'");
        for (Map<String, Object> r : before) {
            jdbc.update("UPDATE quick_link SET sort_no = ?, display_yn = ?, use_yn = ? WHERE quick_link_id = ?",
                    r.get("sort_no"), r.get("display_yn"), r.get("use_yn"), r.get("quick_link_id"));
        }
    }

    @Test
    @DisplayName("공개 경로: 비로그인은 메인 목록만 읽는다 — 관리 목록은 401, 메뉴 권한 없는 회원은 관리 목록·등록 403")
    void publicMainOnly() throws Exception {
        assertThat(post(BASE + "selectQuickLinkListMain.do", "{}", null).statusCode()).isEqualTo(200);
        assertThat(post(BASE + "selectQuickLinkList.do", "{}", null).statusCode()).isEqualTo(401);
        String user = accessToken("user", "user1234!");
        assertThat(post(BASE + "selectQuickLinkList.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(post(BASE + "insertQuickLink.do", "{\"name\":\"ZZ회원\",\"linkUrl\":\"/gen\"}", user).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("기초데이터 예시 4개가 순서대로 메인에 나오고, 공개 목록엔 화면에 필요한 값만 실린다(등록자·IP·노출 여부 없음)")
    void seededMainList() throws Exception {
        String body = post(BASE + "selectQuickLinkListMain.do", "{}", null).body();
        List<String> names = JsonPath.read(body, "$.data[*].name");
        // 이 저장소 예시 — 모집·공지·FAQ·통합검색(CMS 예시는 공지·FAQ·갤러리·통합검색)
        assertThat(names).containsSubsequence("모집 찾기", "공지사항", "자주 묻는 질문", "통합검색");
        List<String> urls = JsonPath.read(body, "$.data[*].linkUrl");
        assertThat(urls).contains("/gen/recruit", "/gen/board/1", "/gen/search");
        assertThat(JsonPath.<List<String>>read(body, "$.data[*].icon")).contains("group", "board", "search");
        assertThat(body).doesNotContain("regId", "updIp", "regIp", "displayYn", "useYn");
    }

    @Test
    @DisplayName("등록하면 맨 뒤 순서, 숨김은 메인에서만 빠지고 관리 목록엔 남는다 — 다시 켜면 원래 자리, 삭제하면 둘 다에서 빠지고 뒤 순서가 당겨진다")
    void displayAndDeleteRules() throws Exception {
        String a = insert("{\"name\":\"ZZ보이는\",\"linkUrl\":\"/gen/board/1\",\"icon\":\"board\"}");
        String b = insert("{\"name\":\"ZZ숨긴\",\"linkUrl\":\"https://example.com/a?b=1\",\"displayYn\":\"N\",\"targetYn\":\"Y\"}");
        String c = insert("{\"name\":\"ZZ마지막\",\"linkUrl\":\"/gen/search\"}");
        int sa = sort(a);
        assertThat(sort(b)).isEqualTo(sa + 1);
        assertThat(sort(c)).isEqualTo(sa + 2);
        assertThat(sa).as("기존 것보다 뒤").isGreaterThan(maxSortExcept(a, b, c));

        assertThat(mainIds()).contains(a, c).doesNotContain(b);
        assertThat(adminIds()).contains(a, b, c);

        String view = post(BASE + "selectQuickLinkView.do", "{\"rowId\":\"" + b + "\"}", admin).body();
        assertThat(JsonPath.<String>read(view, "$.data.linkUrl")).isEqualTo("https://example.com/a?b=1");
        assertThat(JsonPath.<String>read(view, "$.data.targetYn")).isEqualTo("Y");
        assertThat(JsonPath.<String>read(view, "$.data.displayYn")).isEqualTo("N");

        // 다시 켜면 원래 자리(a와 c 사이)
        update(b, "{\"name\":\"ZZ숨긴\",\"linkUrl\":\"https://example.com/a?b=1\",\"displayYn\":\"Y\",\"targetYn\":\"Y\"}");
        List<String> main = mainIds();
        assertThat(main).containsSubsequence(a, b, c);

        assertThat(post(BASE + "deleteQuickLink.do", "{\"rowId\":\"" + a + "\"}", admin).statusCode()).isEqualTo(200);
        assertThat(mainIds()).doesNotContain(a);
        assertThat(adminIds()).doesNotContain(a);
        assertThat(sort(b)).as("뒤 순서가 당겨진다").isEqualTo(sa);
        assertThat(sort(c)).isEqualTo(sa + 1);
        // 이미 지운 것을 또 지워도(더블클릭·재전송) 뒤 순서가 한 번 더 당겨지면 안 된다 — 자리가 겹친다
        assertThat(post(BASE + "deleteQuickLink.do", "{\"rowId\":\"" + a + "\"}", admin).statusCode()).isEqualTo(200);
        assertThat(sort(b)).isEqualTo(sa);
        assertThat(sort(c)).isEqualTo(sa + 1);
        assertThat(JsonPath.using(LENIENT).parse(post(BASE + "selectQuickLinkView.do", "{\"rowId\":\"" + a + "\"}", admin).body())
                .<Object>read("$.data")).as("지운 것은 상세도 없다").isNull();
    }

    @Test
    @DisplayName("순서: ▲는 바로 위와, ▼는 바로 아래와 바꾼다 — 숨긴 것도 자리를 가진다, 맨 끝에서 더 가면 그대로")
    void sortSwap() throws Exception {
        String x = insert("{\"name\":\"ZZ순서X\",\"linkUrl\":\"/gen\"}");
        String h = insert("{\"name\":\"ZZ순서숨김\",\"linkUrl\":\"/gen\",\"displayYn\":\"N\"}");
        String y = insert("{\"name\":\"ZZ순서Y\",\"linkUrl\":\"/gen\"}");
        int sx = sort(x);

        move(y, "UP"); // y ↔ 숨김
        assertThat(sort(y)).isEqualTo(sx + 1);
        assertThat(sort(h)).isEqualTo(sx + 2);
        move(y, "UP"); // y ↔ x
        assertThat(sort(y)).isEqualTo(sx);
        assertThat(sort(x)).isEqualTo(sx + 1);
        move(x, "DOWN"); // x ↔ 숨김
        assertThat(sort(x)).isEqualTo(sx + 2);
        assertThat(sort(h)).isEqualTo(sx + 1);

        move(x, "DOWN"); // 맨 끝 — 그대로
        assertThat(sort(x)).isEqualTo(sx + 2);
        assertThat(mainIds()).containsSubsequence(y, x).doesNotContain(h);
    }

    @Test
    @DisplayName("주소는 사이트 안 경로(/…)나 http(s)://만 — javascript:·data:·//다른사이트·/\\·공백·주소 없는 도메인은 400")
    void linkUrlRules() throws Exception {
        for (String ok : List.of("/gen", "/gen/board/1?postId=3#c", "https://example.com", "http://example.com:8080/a/b?c=d")) {
            HttpResponse<String> r = post(BASE + "insertQuickLink.do", json("ZZ주소", ok), admin);
            assertThat(r.statusCode()).as(ok).isEqualTo(200);
        }
        for (String bad : List.of("javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,x", "//evil.example", "/\\evil.example",
                "example.com", "ftp://example.com", "https://", "/gen board", "https://exa mple.com", " ", "/gen　board", "/gen\u0085x")) {
            HttpResponse<String> r = post(BASE + "insertQuickLink.do", json("ZZ나쁜주소", bad), admin);
            assertThat(r.statusCode()).as(bad).isEqualTo(400);
        }
        // 수정도 같은 검사
        String id = insert(json("ZZ수정검사", "/gen"));
        assertThat(post(BASE + "updateQuickLink.do", "{\"rowId\":\"" + id + "\",\"name\":\"ZZ수정검사\",\"linkUrl\":\"javascript:alert(1)\"}", admin)
                .statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT link_url FROM quick_link WHERE quick_link_id = ?::integer", String.class, id)).isEqualTo("/gen");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quick_link WHERE name = 'ZZ나쁜주소'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("입력 정리: 이름·주소 필수, 이름 50자·주소 255자까지(넘으면 400), 앞뒤 공백 제거, 아이콘은 영문 소문자·숫자·하이픈만, 열기·노출은 Y/N 외 값이면 기본값")
    void inputRules() throws Exception {
        assertThat(post(BASE + "insertQuickLink.do", "{\"name\":\"\",\"linkUrl\":\"/gen\"}", admin).statusCode()).isEqualTo(400);
        assertThat(post(BASE + "insertQuickLink.do", "{\"name\":\"ZZ주소없음\"}", admin).statusCode()).isEqualTo(400);
        assertThat(post(BASE + "insertQuickLink.do", json("ZZ" + "가".repeat(48), "/gen"), admin).statusCode()).isEqualTo(200);
        assertThat(post(BASE + "insertQuickLink.do", json("ZZ" + "가".repeat(49), "/gen"), admin).statusCode()).isEqualTo(400);
        assertThat(post(BASE + "insertQuickLink.do", json("ZZ긴주소", "/" + "a".repeat(254)), admin).statusCode()).isEqualTo(200);
        assertThat(post(BASE + "insertQuickLink.do", json("ZZ긴주소2", "/" + "a".repeat(255)), admin).statusCode()).isEqualTo(400);
        for (String bad : List.of("<b>", "a b", "Board", "x\"y")) {
            assertThat(post(BASE + "insertQuickLink.do", "{\"name\":\"ZZ아이콘\",\"linkUrl\":\"/gen\",\"icon\":\"" + bad.replace("\"", "\\\"") + "\"}", admin)
                    .statusCode()).as(bad).isEqualTo(400);
        }

        String id = insert("{\"name\":\"  ZZ공백  \",\"linkUrl\":\"  /gen/search  \",\"icon\":\"\",\"targetYn\":\"X\",\"displayYn\":\"Q\"}");
        Map<String, Object> row = jdbc.queryForMap("SELECT name, link_url, icon, target_yn, display_yn FROM quick_link WHERE quick_link_id = ?::integer",
                Integer.parseInt(id));
        assertThat(row.get("name")).isEqualTo("ZZ공백");
        assertThat(row.get("link_url")).isEqualTo("/gen/search");
        assertThat(row.get("icon")).as("빈 아이콘은 없음(화면이 기본 그림)").isNull();
        assertThat(row.get("target_yn")).as("모르는 값은 현재 창").isEqualTo("N");
        assertThat(row.get("display_yn")).as("모르는 값은 노출").isEqualTo("Y");
    }

    // ===== helpers =====

    private static String json(String name, String url) {
        return "{\"name\":\"" + name + "\",\"linkUrl\":\"" + url.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private String insert(String json) throws Exception {
        HttpResponse<String> r = post(BASE + "insertQuickLink.do", json, admin);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return JsonPath.read(r.body(), "$.data");
    }

    private void update(String id, String json) throws Exception {
        String body = "{\"rowId\":\"" + id + "\"," + json.substring(1);
        HttpResponse<String> r = post(BASE + "updateQuickLink.do", body, admin);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    }

    private void move(String id, String dir) throws Exception {
        assertThat(post(BASE + "updateQuickLinkSort.do", "{\"rowId\":\"" + id + "\",\"direction\":\"" + dir + "\"}", admin).statusCode())
                .isEqualTo(200);
    }

    private int sort(String id) {
        return jdbc.queryForObject("SELECT sort_no FROM quick_link WHERE quick_link_id = ?::integer", Integer.class, id);
    }

    private int maxSortExcept(String... ids) {
        Integer m = jdbc.queryForObject("SELECT MAX(sort_no) FROM quick_link WHERE use_yn = 'Y' AND quick_link_id NOT IN (?::integer, ?::integer, ?::integer)",
                Integer.class, (Object[]) ids);
        return m == null ? 0 : m;
    }

    private List<String> mainIds() throws Exception {
        return JsonPath.read(post(BASE + "selectQuickLinkListMain.do", "{}", null).body(), "$.data[*].rowId");
    }

    private List<String> adminIds() throws Exception {
        return JsonPath.read(post(BASE + "selectQuickLinkList.do", "{\"pageSize\":100}", admin).body(), "$.data.list[*].rowId");
    }
}
