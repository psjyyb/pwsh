package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색 보조(CMS 1.2.39) — 동의어(검색어 전체가 묶음의 한 말과 같을 때) · 자동완성(자주 검색된 말에서) · 오타 교정(결과 0건일 때 제안만).
 *
 * <p>조용히 틀릴 수 있는 곳 — 동의어가 네 유형(취미·모집·게시글·페이지) 중 일부에만 적용되는 것, 동의어로 찾은 글의 본문 조각이
 * 엉뚱한 곳(본문 맨 앞)을 보여 주는 것, 한 말이 두 묶음에 들어가 결과가 검색어마다 달라지는 것, 자동완성·교정 제안에 인기 검색어가 거른 말
 * (한두 번 들어온 말·결과 없던 말·금칙어·숨긴 말)이 새는 것, 그리고 동의어 관리를 관리자가 아닌 사람이 하는 것.
 */
class SearchAssistTest extends IntegrationTest {

    /** 기초데이터의 공개 게시판(공지사항=1) */
    private static final String NOTICE_BOARD = "1";

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = accessToken("admin", "admin1234!");
        jdbc.update("DELETE FROM search_keyword_daily");
        jdbc.update("DELETE FROM search_keyword_hide");
        jdbc.update("DELETE FROM search_synonym");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM search_keyword_daily");
        jdbc.update("DELETE FROM search_keyword_hide");
        jdbc.update("DELETE FROM search_synonym");
        jdbc.update("DELETE FROM banword WHERE word LIKE 'zz%'");
        jdbc.update("DELETE FROM post WHERE title LIKE 'ZZ보조%'");
        jdbc.update("DELETE FROM page WHERE title LIKE 'ZZ보조%'");
        // 모집·취미는 다른 표가 붙어 있을 수 있어 지우지 않고 내린다(검색은 use_yn='Y'만 본다)
        jdbc.update("UPDATE recruit SET use_yn = 'N' WHERE title LIKE 'ZZ보조%'");
        jdbc.update("UPDATE hobby SET use_yn = 'N' WHERE name LIKE 'ZZ보조%'");
        jdbc.update("DELETE FROM auth WHERE conn_id = 'MEMBER' AND menu_id IN (SELECT menu_id FROM menu WHERE link_url = '/adm/searchsynonym')");
    }

    // ===== 동의어 =====

    @Test
    @DisplayName("동의어: 검색어 전체가 묶음의 한 말과 같으면 취미·모집·게시글·페이지 모두 묶음 전체로 찾는다 — 다른 말이 붙으면 적용하지 않는다, 본문 조각은 찾은 말 자리에서")
    void synonymExpandsWholeKeyword() throws Exception {
        String hobbyId = newHobby("ZZ보조취미 zz sign up", "취미 소개");
        newHobby("ZZ보조취미 둘", "여기서 zz회원가입 이야기");
        newRecruit(hobbyId, "ZZ보조 zz회원가입 모임", "모집 설명");
        newRecruit(hobbyId, "ZZ보조 모임 둘", "zz sign up 하고 오세요");
        // 제목에 동의어가 든 것을 먼저 만든다 — 번호순(나중 것 먼저)이 아니라 제목 일치가 앞에 와야 한다
        newPost("ZZ보조 zz sign up 안내", "본문");
        newPost("ZZ보조 회원 안내", "<p>" + "가".repeat(150) + " 여기서 zz회원가입 절차를 설명합니다</p>"); // 본문 앞 120자 안에 없게
        insertPage("ZZ보조 zz회원가입 페이지", "본문");
        insertPage("ZZ보조 안내 페이지", "<p>" + "나".repeat(150) + " 페이지의 zz sign up 순서</p>");
        assertThat(total(search("zz가입"))).as("동의어 등록 전").isZero();

        assertThat(post("/api/adm/searchsynonym/insertSearchSynonym.do", "{\"words\":\"zz가입, ZZ회원가입\\nzz sign up\"}", admin)
                .statusCode()).isEqualTo(200);
        String body = search("ZZ가입");
        assertThat(JsonPath.<Integer>read(body, "$.data.hobbyCount")).as("취미 이름·소개").isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.data.recruitCount")).as("모집 제목·설명").isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.data.postCount")).isEqualTo(2);
        assertThat(JsonPath.<String>read(body, "$.data.posts[0].title")).as("제목 일치 먼저").contains("sign up");
        assertThat(JsonPath.<String>read(body, "$.data.posts[1].searchSnippet")).as("동의어가 나온 자리의 문맥").contains("zz회원가입 절차");
        assertThat(JsonPath.<Integer>read(body, "$.data.pageCount")).as("일반페이지도 동의어로").isEqualTo(2);
        assertThat(JsonPath.<String>read(body, "$.data.pages[0].title")).contains("zz회원가입");
        assertThat(JsonPath.<String>read(body, "$.data.pages[1].searchSnippet")).contains("zz sign up 순서");
        assertThat(total(search("zz가입 방법"))).as("검색어 전체가 같을 때만").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM search_keyword_daily WHERE keyword = 'zz가입'", Integer.class))
                .as("통계는 입력한 말 그대로").isEqualTo(1);

        String id = jdbc.queryForObject("SELECT synonym_id::text FROM search_synonym WHERE use_yn = 'Y'", String.class);
        assertThat(post("/api/adm/searchsynonym/deleteSearchSynonym.do", "{\"rowId\":\"" + id + "\"}", admin).statusCode()).isEqualTo(200);
        assertThat(total(search("zz가입"))).as("묶음을 지우면 다시 그 말만").isZero();
    }

    @Test
    @DisplayName("동의어 묶음: 서로 다른 말 2개 이상·한 말은 한 묶음에만 — 정리(공백·대소문자·중복)해서 저장하고, 고칠 땐 자기 묶음의 말은 그대로 둬도 된다")
    void synonymValidation() throws Exception {
        assertThat(insertSynonym("zz하나")).isEqualTo(400);
        assertThat(insertSynonym("zz하나, ZZ하나 ")).as("정리하면 같은 말 하나").isEqualTo(400);
        assertThat(insertSynonym("zz사과,  ZZ  애플 ,zz사과")).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT words FROM search_synonym WHERE use_yn = 'Y'", String.class)).isEqualTo("zz사과,zz 애플");
        assertThat(insertSynonym("zz애플, zz 애플")).as("'zz 애플'은 이미 다른 묶음에").isEqualTo(400);

        String id = jdbc.queryForObject("SELECT synonym_id::text FROM search_synonym WHERE use_yn = 'Y'", String.class);
        assertThat(post("/api/adm/searchsynonym/updateSearchSynonym.do", "{\"rowId\":\"" + id + "\",\"words\":\"zz사과, zz 애플, zz능금\"}", admin)
                .statusCode()).as("자기 묶음의 말은 겹침이 아니다").isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT words FROM search_synonym WHERE synonym_id = ?::integer", String.class, id))
                .isEqualTo("zz사과,zz 애플,zz능금");
        String list = post("/api/adm/searchsynonym/selectSearchSynonymList.do", "{\"filterKeyword\":\"능금\"}", admin).body();
        assertThat(JsonPath.<Integer>read(list, "$.data.totalCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("동의어 묶음 한도: 말 20개·말마다 50자까지(경계 포함) — 목록 찾기의 LIKE 특수문자는 글자 그대로")
    void synonymLimits() throws Exception {
        assertThat(insertSynonym(words("zz많은", 21))).as("21개").isEqualTo(400);
        assertThat(insertSynonym(words("zz많은", 20))).as("20개").isEqualTo(200);
        assertThat(insertSynonym("zz" + "가".repeat(49) + ",zz짧은말")).as("51자").isEqualTo(400);
        assertThat(insertSynonym("zz" + "나".repeat(48) + ",zz짧은말")).as("50자").isEqualTo(200);

        String percent = post("/api/adm/searchsynonym/selectSearchSynonymList.do", "{\"filterKeyword\":\"%\"}", admin).body();
        assertThat(JsonPath.<Integer>read(percent, "$.data.totalCount")).isZero();
        String hit = post("/api/adm/searchsynonym/selectSearchSynonymList.do", "{\"filterKeyword\":\"zz많은7\"}", admin).body();
        assertThat(JsonPath.<Integer>read(hit, "$.data.totalCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("동의어 관리는 관리자 화면 권한이 있어야 한다 — 비로그인 401, 일반 회원 403")
    void synonymNeedsAdminMenu() throws Exception {
        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/searchsynonym/selectSearchSynonymList.do", "{}", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/adm/searchsynonym/selectSearchSynonymList.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/searchsynonym/insertSearchSynonym.do", "{\"words\":\"zz가,zz나\"}", user).statusCode()).isEqualTo(403);
    }

    // ===== 자동완성 =====

    @Test
    @DisplayName("자동완성(비로그인 공개): 최근 30일 2회 이상·결과가 있던 말 중 앞이 맞는 말 먼저, 그다음 중간에 든 말 — 인기 검색어가 거르는 말은 여기서도 빠진다")
    void autocomplete() throws Exception {
        seed("zz자동완성", 0, 5, 0);
        seed("zz자동차", 3, 3, 0);
        seed("새 zz자동문", 1, 9, 0); // 중간에 든다 — 더 많이 검색됐어도 앞이 맞는 말 뒤에
        seed("zz자동문", 0, 1, 0);   // 한 번뿐
        seed("zz자동결과없음", 0, 4, 4);
        seed("zz자동옛말", 40, 9, 0); // 30일 전보다 오래됐다
        seed("zz자동광고", 0, 6, 0);
        seed("zz자동숨김", 0, 7, 0);
        post("/api/adm/banword/insertBanword.do", "{\"word\":\"zz자동광\"}", admin);
        post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\"zz자동숨김\",\"hideYn\":\"Y\"}", admin);

        assertThat(suggest(" ZZ자동 ")).containsExactly("zz자동완성", "zz자동차", "새 zz자동문");
        assertThat(suggest("   ")).as("빈 입력은 조회하지 않는다").isEmpty();
        assertThat(suggest("%")).as("LIKE 특수문자는 글자 그대로").isEmpty();

        for (int i = 1; i <= 10; i++) {
            seed("zz한도" + i, 0, 2, 0);
        }
        assertThat(suggest("zz한도")).as("8개까지").hasSize(8);
    }

    // ===== 오타 교정 =====

    @Test
    @DisplayName("오타 교정: 결과가 0건일 때만 자주 검색되고 결과가 있던 말 중 가장 가까운 말을 제안한다 — 너무 다르면·금칙어·숨긴 말은 제안하지 않는다")
    void typoSuggestion() throws Exception {
        seed("zz프로그래밍", 0, 5, 0);
        seed("zz데이타베이스광고", 0, 5, 0);
        seed("zz자바스크립트", 0, 5, 0);
        post("/api/adm/banword/insertBanword.do", "{\"word\":\"zz데이타베이스광\"}", admin);
        post("/api/adm/searchstat/updateSearchStatHide.do", "{\"keyword\":\"zz자바스크립트\",\"hideYn\":\"Y\"}", admin);

        assertThat(suggestOf(search("zz프로그레밍"))).as("모음 하나 틀림").isEqualTo("zz프로그래밍");
        assertThat(suggestOf(search("ZZ프로 그래밍"))).as("띄어쓰기가 달라도").isEqualTo("zz프로그래밍");
        assertThat(suggestOf(search("zz전혀다른말"))).as("너무 다르면 제안 없음").isNull();
        assertThat(suggestOf(search("zz데이타베이스광구"))).as("금칙어가 든 말").isNull();
        assertThat(suggestOf(search("zz자바스크립뜨"))).as("숨긴 말").isNull();

        // 같은 오타라도 그 말로 쓴 글이 있으면(결과 1건) 제안하지 않는다
        newPost("ZZ보조 zz프로그레밍 글", "본문");
        assertThat(suggestOf(search("zz프로그레밍"))).as("결과가 있으면 제안하지 않는다").isNull();
    }

    @Test
    @DisplayName("오타 교정 허용 차이는 검색어 길이(자모)에 따라 — 6자모 이하 1, 15 이하 2, 그보다 길면 3 (경계 양쪽)")
    void typoAllowanceByLength() throws Exception {
        seed("zz가나", 0, 5, 0);          // 자모 6
        seed("zz가난", 0, 5, 0);          // 자모 7
        seed("zz바다여행가자", 0, 5, 0);  // 자모 15
        seed("zz산책길걷기", 0, 5, 0);    // 자모 16

        assertThat(suggestOf(search("zz가너"))).as("6자모·1 차이").isEqualTo("zz가나");
        assertThat(suggestOf(search("zz거너"))).as("6자모·2 차이").isNull();
        assertThat(suggestOf(search("zz거넌"))).as("7자모·2 차이").isEqualTo("zz가난");
        assertThat(suggestOf(search("zz버더여행가자"))).as("15자모·2 차이").isEqualTo("zz바다여행가자");
        assertThat(suggestOf(search("zz버더여헹가자"))).as("15자모·3 차이").isNull();
        assertThat(suggestOf(search("zz선첵길겉기"))).as("16자모·3 차이").isEqualTo("zz산책길걷기");
        assertThat(suggestOf(search("zz선첵길겉거"))).as("16자모·4 차이").isNull();
    }

    @Test
    @DisplayName("오타 교정은 가장 가까운 말, 같은 거리면 더 많이 검색된 말 — 검색어 자신(결과가 사라진 옛 인기어)은 제안하지 않는다")
    void typoPrefersClosestThenMostSearched() throws Exception {
        seed("zz프로그래밍", 0, 9, 0); // 1 차이
        seed("zz프로그레빙", 0, 4, 0); // 1 차이, 덜 검색됨
        seed("zz프로그래빙", 0, 3, 0); // 2 차이
        assertThat(suggestOf(search("zz프로그레밍"))).isEqualTo("zz프로그래밍");

        seed("zz사라진말", 0, 5, 0); // 예전엔 결과가 있었지만 지금은 글이 없다
        assertThat(suggestOf(search("zz사라진말"))).isNull();
    }

    // ===== helpers =====

    private void seed(String keyword, int daysAgo, int searchCnt, int zeroCnt) {
        jdbc.update("INSERT INTO search_keyword_daily (stat_date, keyword, search_cnt, zero_cnt)"
                + " VALUES (CURRENT_DATE - ?::integer, ?, ?, ?)", daysAgo, keyword, searchCnt, zeroCnt);
    }

    /** 이 저장소의 통합검색은 selectSearchAll.do + filterKeyword(CMS는 selectSearchList.do + keyword) */
    private String search(String keyword) throws Exception {
        HttpResponse<String> r = post("/api/adm/search/selectSearchAll.do", "{\"filterKeyword\":\"" + keyword + "\"}", admin);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return r.body();
    }

    private int total(String body) {
        return JsonPath.<Integer>read(body, "$.data.hobbyCount") + JsonPath.<Integer>read(body, "$.data.recruitCount")
                + JsonPath.<Integer>read(body, "$.data.postCount") + JsonPath.<Integer>read(body, "$.data.pageCount");
    }

    /** 응답이 null 필드를 싣지 않을 수 있다 — 제안이 없으면 suggest 자체가 없다(null로 읽는다) */
    private String suggestOf(String body) {
        return JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL))
                .parse(body).read("$.data.suggest");
    }

    private List<String> suggest(String keyword) throws Exception {
        return JsonPath.read(post("/api/adm/search/selectSearchListSuggest.do", "{\"keyword\":\"" + keyword + "\"}", null).body(), "$.data");
    }

    private void newPost(String title, String content) throws Exception {
        HttpResponse<String> r = post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + NOTICE_BOARD + "\",\"title\":\"" + title + "\",\"content\":\"" + content + "\"}", admin);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    }

    private String newHobby(String name, String summary) throws Exception {
        HttpResponse<String> r = post("/api/adm/hobby/insertHobby.do", "{\"hobbyName\":\"" + name + "\",\"summary\":\"" + summary + "\"}", admin);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return JsonPath.read(r.body(), "$.data");
    }

    private void newRecruit(String hobbyId, String title, String content) throws Exception {
        HttpResponse<String> r = post("/api/adm/recruit/insertRecruit.do", "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"" + title
                + "\",\"content\":\"" + content + "\",\"meetDt\":\"" + LocalDate.now().plusDays(4) + "\"}", admin);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    }

    private void insertPage(String title, String content) {
        jdbc.update("INSERT INTO page (title, content, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (?, ?, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", title, content);
    }

    private int insertSynonym(String words) throws Exception {
        return post("/api/adm/searchsynonym/insertSearchSynonym.do", "{\"words\":\"" + words + "\"}", admin).statusCode();
    }

    private static String words(String prefix, int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> prefix + i).collect(Collectors.joining(","));
    }
}
