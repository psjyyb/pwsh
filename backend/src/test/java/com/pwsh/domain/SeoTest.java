package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.configitem.service.ConfigItemService;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * RSS · SEO(CMS 1.2.43) — 공개 게시판 RSS, sitemap.xml, robots.txt, 사이트 설명·키워드(확장설정), 설치 점검의 사이트 주소.
 *
 * <p>조용히 틀릴 수 있는 곳 — 로그인 없이 읽히는 RSS·sitemap으로 비공개 게시판·비밀글·승인 전 글·삭제된 글이 새는 것,
 * 제목에 든 {@code < & "}가 XML을 깨뜨리는 것, 요청 Host로 링크를 만들어 조작당하는 것(사용자 결정: 확장설정 '사이트 주소'만 쓰고 비면 끈다).
 */
class SeoTest extends IntegrationTest {

    private static final String NOTICE = "1";   // 기초데이터 공지사항 — GUEST 열람
    private static final String QNA = "4";      // 1:1 문의 — 비밀글만, GUEST 메뉴 권한 없음
    private static final String HOBBY = "1";    // 기초데이터 취미 '등산'
    private static final String HOBBY_BOARD = "5"; // 그 취미 게시판 — GEN 메뉴가 없어도 커뮤니티 공개
    private static final String SITE = "https://example.test";

    @Autowired
    private ConfigItemService configItemService;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = accessToken("admin", "admin1234!");
        siteUrl(SITE);
    }

    @AfterEach
    void cleanup() {
        siteUrl("");
        jdbc.update("DELETE FROM auth WHERE conn_id = 'GUEST' AND menu_id IN (SELECT menu_id FROM menu WHERE area = 'GEN'"
                + " AND conn_cd = 'MENU02' AND conn_id = 4)");
        jdbc.update("DELETE FROM auth WHERE menu_id IN (SELECT menu_id FROM menu WHERE name LIKE 'ZZ검색엔진%')");
        jdbc.update("DELETE FROM menu WHERE name LIKE 'ZZ검색엔진%'");
        jdbc.update("UPDATE board SET approval_yn = 'N' WHERE board_id = 1");
        jdbc.update("UPDATE hobby SET use_yn = 'Y' WHERE hobby_id = " + HOBBY);
        jdbc.update("DELETE FROM hobby WHERE name LIKE 'ZZ검색엔진%'");
        jdbc.update("DELETE FROM recruit WHERE title LIKE 'ZZ검색엔진%'");
        jdbc.update("DELETE FROM post WHERE title LIKE '%ZZ검색엔진%'");
        jdbc.update("DELETE FROM board WHERE name LIKE 'ZZ검색엔진%'");
    }

    // ===== RSS =====

    @Test
    @DisplayName("RSS: 공개 게시판의 공개 원글만 최신순 — 비밀글·승인 전 글·삭제된 글·답글은 빠지고, 링크는 사이트 주소로, 본문은 태그를 지운 앞부분")
    void rssListsPublicPostsOnly() throws Exception {
        // 제목 끝의 \u0001은 XML 1.0에 넣을 수 없는 제어문자 — 그대로 내보내면 RSS 리더가 파싱을 포기한다
        String pub = newPost(admin, "ZZ검색엔진 공개 <b>&\"따옴표\"\u0001", "<p>" + "가나다 &amp; 라 ".repeat(40) + "</p>");
        String secret = newPost(admin, "ZZ검색엔진 비밀", "본문", ",\"secretYn\":\"Y\",\"password\":\"pw1234\"");
        String deleted = newPost(admin, "ZZ검색엔진 삭제", "본문", "");
        assertThat(post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + deleted + "\"}", admin).statusCode()).isEqualTo(200);
        String reply = newPost(admin, "RE: ZZ검색엔진 답글", "본문", ",\"pPostId\":\"" + pub + "\"");
        jdbc.update("UPDATE board SET approval_yn = 'Y' WHERE board_id = 1");
        String pending = newPost(accessToken("user", "user1234!"), "ZZ검색엔진 승인대기", "본문", "");

        HttpResponse<String> r = get("/api/pub/seo/rss/" + NOTICE, null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Type").orElse("")).contains("xml");
        Document doc = xml(r.body());
        assertThat(text(doc, "channel", "title")).contains("공지사항");
        assertThat(text(doc, "channel", "link")).isEqualTo(SITE + "/gen/board/" + NOTICE);

        List<Map<String, String>> items = items(doc);
        List<String> links = items.stream().map(i -> i.get("link")).toList();
        assertThat(links).allMatch(l -> l.startsWith(SITE + "/gen/board/" + NOTICE + "?post="));
        assertThat(links).contains(link(pub)).doesNotContain(link(secret), link(deleted), link(reply), link(pending));
        Map<String, String> item = items.stream().filter(i -> link(pub).equals(i.get("link"))).findFirst().orElseThrow();
        assertThat(item.get("title")).as("특수문자가 XML을 깨지 않고 그대로, 제어문자는 뺀다").isEqualTo("ZZ검색엔진 공개 <b>&\"따옴표\"");
        assertThat(item.get("description")).as("태그·엔티티를 푼 본문 앞부분").startsWith("가나다 & 라").doesNotContain("<p>").endsWith("…");
        assertThat(item.get("description").length()).isLessThanOrEqualTo(201);
        assertThat(item.get("pubDate")).as("RFC 1123").matches("[A-Z][a-z]{2}, \\d{1,2} [A-Z][a-z]{2} \\d{4} .*");
    }

    @Test
    @DisplayName("RSS는 비로그인이 읽을 수 있는 게시판만 — 1:1 문의·메뉴에 안 걸린 게시판·없는 게시판은 404, 사이트 주소가 비면 끈다(404)")
    void rssOnlyForPublicBoards() throws Exception {
        assertThat(get("/api/pub/seo/rss/" + QNA, null).statusCode()).isEqualTo(404);
        // 1:1 문의는 비로그인 메뉴 권한을 줘도 RSS가 없다(글이 전부 비밀글)
        jdbc.update("INSERT INTO auth (menu_id, conn_id, type, menu_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " SELECT menu_id, 'GUEST', 'GRP', 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'"
                + " FROM menu WHERE area = 'GEN' AND conn_cd = 'MENU02' AND conn_id = 4");
        assertThat(get("/api/pub/seo/rss/" + QNA, null).statusCode()).as("1:1 문의 + GUEST 권한").isEqualTo(404);
        jdbc.update("INSERT INTO board (name, type_cd, description, list_cnt, file_yn, file_cnt_limit, file_size_limit_mb, notice_yn,"
                + " new_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES ('ZZ검색엔진 숨은 게시판', 'BOARD01', 'x', 10,"
                + " 'N', 5, 10, 'N', 0, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')");
        String hidden = jdbc.queryForObject("SELECT board_id::text FROM board WHERE name = 'ZZ검색엔진 숨은 게시판'", String.class);
        assertThat(get("/api/pub/seo/rss/" + hidden, null).statusCode()).as("GEN 메뉴(GUEST 권한)가 없는 게시판").isEqualTo(404);
        assertThat(get("/api/pub/seo/rss/999999", null).statusCode()).isEqualTo(404);
        assertThat(get("/api/pub/seo/rss/abc", null).statusCode()).isNotEqualTo(200);

        siteUrl("");
        assertThat(get("/api/pub/seo/rss/" + NOTICE, null).statusCode()).as("사이트 주소가 없으면 끈다").isEqualTo(404);
        siteUrl("example.test/no-scheme");
        assertThat(get("/api/pub/seo/rss/" + NOTICE, null).statusCode()).as("형식이 틀린 주소도 끈다").isEqualTo(404);
        siteUrl(SITE + "/");
        assertThat(text(xml(get("/api/pub/seo/rss/" + NOTICE, null).body()), "channel", "link"))
                .as("끝의 / 는 떼고 쓴다").isEqualTo(SITE + "/gen/board/" + NOTICE);
    }

    @Test
    @DisplayName("게시판 상세의 rssYn — 사이트 주소가 있고 비로그인이 읽는 게시판(1:1 문의 제외)일 때만 Y(화면이 RSS 링크를 띄운다)")
    void boardViewTellsRss() throws Exception {
        assertThat(boardRss(NOTICE, null)).isEqualTo("Y");
        assertThat(boardRss(HOBBY_BOARD, null)).as("취미 게시판").isEqualTo("Y");
        assertThat(boardRss(QNA, admin)).isEqualTo("N");
        siteUrl("");
        assertThat(boardRss(NOTICE, null)).isEqualTo("N");
    }

    @Test
    @DisplayName("취미 게시판 RSS — GEN 메뉴가 없어도 커뮤니티 공개라 준다(링크 ?post=), 취미를 내리면 끈다(404)")
    void hobbyBoardRss() throws Exception {
        HttpResponse<String> w = post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + HOBBY_BOARD + "\",\"title\":\"ZZ검색엔진 취미글\",\"content\":\"본문\"}", admin);
        assertThat(w.statusCode()).as(w.body()).isEqualTo(200);
        String id = JsonPath.read(w.body(), "$.data");

        HttpResponse<String> r = get("/api/pub/seo/rss/" + HOBBY_BOARD, null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(items(xml(r.body())).stream().map(i -> i.get("link")))
                .contains(SITE + "/gen/board/" + HOBBY_BOARD + "?post=" + id);

        jdbc.update("UPDATE hobby SET use_yn = 'N' WHERE hobby_id = " + HOBBY);
        assertThat(get("/api/pub/seo/rss/" + HOBBY_BOARD, null).statusCode()).as("내린 취미의 게시판").isEqualTo(404);
    }

    // ===== sitemap · robots =====

    @Test
    @DisplayName("sitemap.xml: 비로그인 공개 메뉴 화면 + 공개 게시판의 공개 원글 — 비밀글·승인 전 글·비공개 메뉴(1:1·마이페이지)는 없다, 사이트 주소가 비면 404")
    void sitemapListsPublicUrls() throws Exception {
        String pub = newPost(admin, "ZZ검색엔진 지도 공개", "본문", "");
        String secret = newPost(admin, "ZZ검색엔진 지도 비밀", "본문", ",\"secretYn\":\"Y\",\"password\":\"pw1234\"");
        jdbc.update("UPDATE board SET approval_yn = 'Y' WHERE board_id = 1");
        String pending = newPost(accessToken("user", "user1234!"), "ZZ검색엔진 지도 대기", "본문", "");
        // 지워진 게시판을 가리키는 공개 메뉴 — 화면이 404라 sitemap에 넣지 않는다
        jdbc.update("INSERT INTO board (name, type_cd, description, list_cnt, file_yn, file_cnt_limit, file_size_limit_mb, notice_yn,"
                + " new_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES ('ZZ검색엔진 지운 게시판', 'BOARD01', 'x', 10,"
                + " 'N', 5, 10, 'N', 0, 'N', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')");
        String gone = jdbc.queryForObject("SELECT board_id::text FROM board WHERE name = 'ZZ검색엔진 지운 게시판'", String.class);
        jdbc.update("INSERT INTO menu (p_menu_id, area, name, sort_no, conn_cd, conn_id, link_url, target_yn, use_yn,"
                + " reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES (0, 'GEN', 'ZZ검색엔진 지운 메뉴', 99, 'MENU02', ?::integer, NULL, 'N', 'Y',"
                + " 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", gone);
        jdbc.update("INSERT INTO auth (menu_id, conn_id, type, menu_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " SELECT menu_id, 'GUEST', 'GRP', 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'"
                + " FROM menu WHERE name = 'ZZ검색엔진 지운 메뉴'");
        // 비로그인 메뉴가 없는 게시판의 공개 글 — 사용자 화면에 없는 글이라 sitemap에도 없어야 한다
        jdbc.update("INSERT INTO board (name, type_cd, description, list_cnt, file_yn, file_cnt_limit, file_size_limit_mb, notice_yn,"
                + " new_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES ('ZZ검색엔진 비공개 게시판', 'BOARD01', 'x', 10,"
                + " 'N', 5, 10, 'N', 0, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')");
        String hiddenBoard = jdbc.queryForObject("SELECT board_id::text FROM board WHERE name = 'ZZ검색엔진 비공개 게시판'", String.class);
        HttpResponse<String> hp = post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + hiddenBoard + "\",\"title\":\"ZZ검색엔진 비공개 게시판 글\",\"content\":\"본문\"}", admin);
        assertThat(hp.statusCode()).as(hp.body()).isEqualTo(200);
        String hiddenLink = SITE + "/gen/board/" + hiddenBoard + "?post=" + JsonPath.read(hp.body(), "$.data");
        // 내린 취미에 걸린 게시판도 공개가 아니다 — 취미 화면·그 게시판 글 모두 없어야 한다
        jdbc.update("INSERT INTO hobby (name, summary, board_id, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES ('ZZ검색엔진 내린 취미', 'x', ?::integer, 99, 'N', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", hiddenBoard);
        String offHobby = jdbc.queryForObject("SELECT hobby_id::text FROM hobby WHERE name = 'ZZ검색엔진 내린 취미'", String.class);
        // 취미 게시판 글 · 모집(삭제한 모집은 빠진다)
        HttpResponse<String> hw = post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + HOBBY_BOARD + "\",\"title\":\"ZZ검색엔진 지도 취미글\",\"content\":\"본문\"}", admin);
        assertThat(hw.statusCode()).as(hw.body()).isEqualTo(200);
        String hobbyLink = SITE + "/gen/board/" + HOBBY_BOARD + "?post=" + JsonPath.read(hw.body(), "$.data");
        String recruit = newRecruit("ZZ검색엔진 지도 모집");
        String goneRecruit = newRecruit("ZZ검색엔진 지도 지운 모집");
        assertThat(post("/api/adm/recruit/deleteRecruit.do", "{\"rowId\":\"" + goneRecruit + "\"}", admin).statusCode()).isEqualTo(200);

        HttpResponse<String> r = get("/api/pub/seo/sitemap.xml", null);
        assertThat(r.statusCode()).isEqualTo(200);
        Document doc = xml(r.body());
        List<String> locs = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("loc");
        for (int i = 0; i < nodes.getLength(); i++) {
            locs.add(nodes.item(i).getTextContent());
        }
        assertThat(locs).contains(SITE + "/gen/main", SITE + "/gen/board/" + NOTICE, link(pub),
                        SITE + "/gen/recruit", SITE + "/gen/hobby/" + HOBBY, hobbyLink, SITE + "/gen/recruit/" + recruit)
                .doesNotContain(link(secret), link(pending), SITE + "/gen/board/" + QNA, SITE + "/gen/mypage", SITE + "/gen/feed", SITE + "/gen/board/" + gone,
                        hiddenLink, SITE + "/gen/hobby/" + offHobby, SITE + "/gen/recruit/" + goneRecruit);
        assertThat(locs).allMatch(l -> l.startsWith(SITE + "/gen/")).doesNotHaveDuplicates();

        siteUrl("");
        assertThat(get("/api/pub/seo/sitemap.xml", null).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("robots.txt: 관리자·API(공개 제외)·로그인·가입·검색 결과·개인 화면(쪽지·피드·내 취미)은 막고, 사이트 주소가 있으면 sitemap 위치를 알린다")
    void robots() throws Exception {
        HttpResponse<String> r = get("/api/pub/seo/robots.txt", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("User-agent: *", "Disallow: /adm", "Disallow: /api/", "Allow: /api/pub/",
                "Disallow: /signup\n", "Disallow: /forgot\n", "Disallow: /gen/message\n", "Disallow: /gen/feed\n",
                "Disallow: /gen/myhobby\n", "Disallow: /gen/onboarding\n",
                "Sitemap: " + SITE + "/sitemap.xml");
        siteUrl("");
        assertThat(get("/api/pub/seo/robots.txt", null).body()).contains("Disallow: /adm").doesNotContain("Sitemap:");
    }

    // ===== 설정 =====

    @Test
    @DisplayName("사이트 설명·키워드는 비로그인 공개 설정(화면 메타), 사이트 주소는 공개 목록에 없다 — 설치 점검이 사이트 주소를 본다")
    void settingsAndInstallCheck() throws Exception {
        String pub = post("/api/pub/configitem", "{}", null).body();
        List<String> keys = JsonPath.read(pub, "$.data[*].configKey");
        assertThat(keys).contains("seo.description", "seo.keywords").doesNotContain("site.url");

        assertThat(checkLevel()).isEqualTo("OK");
        siteUrl("");
        assertThat(checkLevel()).isEqualTo("WARN");
        siteUrl("ftp://example.test");
        assertThat(checkLevel()).as("http(s)가 아니면 형식 오류").isEqualTo("WARN");
    }

    // ===== helpers =====

    /** 확장설정 사이트 주소 — 설정 캐시(60초)를 바로 비운다 */
    private void siteUrl(String value) {
        jdbc.update("UPDATE config_item SET value = ? WHERE config_key = 'site.url'", value);
        configItemService.evict();
    }

    private String newPost(String token, String title, String content) throws Exception {
        return newPost(token, title, content, "");
    }

    private String newPost(String token, String title, String content, String extra) throws Exception {
        String json = "{\"boardId\":\"" + NOTICE + "\",\"title\":" + quote(title) + ",\"content\":" + quote(content) + extra + "}";
        HttpResponse<String> r = post("/api/adm/post/insertPost.do", json, token);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return JsonPath.read(r.body(), "$.data");
    }

    /** JSON 문자열 — 제어문자는 \\uXXXX로(그대로 넣으면 요청 JSON이 깨진다) */
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private String newRecruit(String title) throws Exception {
        HttpResponse<String> r = post("/api/adm/recruit/insertRecruit.do", "{\"hobbyId\":\"" + HOBBY + "\",\"title\":\"" + title
                + "\",\"meetDt\":\"" + java.time.LocalDate.now().plusDays(7) + "\"}", admin);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return JsonPath.read(r.body(), "$.data");
    }

    private static String link(String postId) {
        return SITE + "/gen/board/" + NOTICE + "?post=" + postId;
    }

    private String boardRss(String boardId, String token) throws Exception {
        return JsonPath.read(post("/api/adm/board/selectBoardView.do", "{\"rowId\":\"" + boardId + "\"}", token).body(), "$.data.rssYn");
    }

    private String checkLevel() throws Exception {
        String body = post("/api/adm/system/selectSystemListCheck.do", "{}", admin).body();
        List<String> lv = JsonPath.read(body, "$.data.list[?(@.key=='SITE_URL')].level");
        assertThat(lv).hasSize(1);
        return lv.get(0);
    }

    private static Document xml(String body) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    /** parent 아래 첫 자식 child의 글자 */
    private static String text(Document doc, String parent, String child) {
        Element p = (Element) doc.getElementsByTagName(parent).item(0);
        NodeList kids = p.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element e && child.equals(e.getTagName())) {
                return e.getTextContent();
            }
        }
        return null;
    }

    private static List<Map<String, String>> items(Document doc) {
        List<Map<String, String>> out = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element item = (Element) nodes.item(i);
            Map<String, String> m = new java.util.HashMap<>();
            NodeList kids = item.getChildNodes();
            for (int k = 0; k < kids.getLength(); k++) {
                if (kids.item(k) instanceof Element e) {
                    m.put(e.getTagName(), e.getTextContent());
                }
            }
            out.add(m);
        }
        return out;
    }
}
