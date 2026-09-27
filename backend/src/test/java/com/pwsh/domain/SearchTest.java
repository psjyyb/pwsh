package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 통합검색 — 취미·모집·게시글·안내페이지를 키워드로 한 번에 찾는다.
 *
 * <p>★ 이 기능의 진짜 위험은 "안 나오는 것"이 아니라 <b>나오면 안 되는 것이 나오는 것</b>이다.
 * 목록·상세는 GenAccessGuard가 막아도, 검색이 제목·본문 조각을 보여주면 열람 불가 콘텐츠가
 * 그대로 샌다. 비밀글도 마찬가지다.
 *
 * <p>이 저장소만의 사정: 게시판이 두 종류다. 취미 게시판은 커뮤니티 공개라 GEN 메뉴가 없고,
 * 공지사항 등은 GEN 메뉴 권한으로 판정한다. 둘 다 검색돼야 한다.
 */
class SearchTest extends IntegrationTest {

    /** 기초데이터의 공개 게시판(공지사항=1, GEN 메뉴에 연결돼 GUEST 권한이 있다) */
    private static final String NOTICE_BOARD = "1";
    private static final String KEYWORD = "zzsearch고유어";

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM post WHERE title LIKE 'ZZ검색%' OR content LIKE '%" + KEYWORD + "%'");
        jdbc.update("DELETE FROM page WHERE title LIKE 'ZZ검색%'");
        jdbc.update("DELETE FROM hobby WHERE name LIKE 'ZZ검색%'");
        jdbc.update("DELETE FROM board WHERE name LIKE 'ZZ검색%'");
    }

    @Test
    @DisplayName("제목·본문에서 찾고, 본문에서 찾은 글에만 문맥 조각이 붙는다")
    void findsByTitleAndContentWithSnippet() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        newPost(admin, NOTICE_BOARD, "ZZ검색 제목에 " + KEYWORD + " 있음", "본문은 평범하다");
        newPost(admin, NOTICE_BOARD, "ZZ검색 제목엔 없음", "앞쪽 문장 " + KEYWORD + " 뒤쪽 문장");

        String body = searchBody(KEYWORD, null);

        assertThat((int) JsonPath.read(body, "$.data.postCount")).isEqualTo(2);
        List<String> titles = JsonPath.read(body, "$.data.posts[*].title");
        // 제목에서 찾은 글이 본문에서만 찾은 글보다 앞에 온다(간단한 관련도 정렬)
        assertThat(titles.get(0)).contains("제목에");

        List<String> snippets = JsonPath.read(body, "$.data.posts[*].searchSnippet");
        assertThat(snippets).hasSize(1);
        assertThat(snippets.get(0)).contains(KEYWORD);
    }

    @Test
    @DisplayName("취미 게시판 글과 공지 게시판 글이 둘 다 검색된다")
    void searchesBothHobbyBoardsAndMenuBoards() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyBoardId = newHobbyBoard(admin, "ZZ검색취미");
        newPost(admin, hobbyBoardId, "ZZ검색 취미글 " + KEYWORD, "본문");
        newPost(admin, NOTICE_BOARD, "ZZ검색 공지글 " + KEYWORD, "본문");

        // ★ 취미 게시판만 보면 공지사항이 검색에 영원히 안 걸린다(CMS 1.2.10 흡수 전 상태)
        assertThat(postCount(KEYWORD, null)).isEqualTo(2);
    }

    @Test
    @DisplayName("열람 권한 없는 게시판의 글은 검색에도 안 나온다")
    void hiddenBoardIsNotSearchable() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 취미도 아니고 GEN 메뉴도 없는 게시판 → 사용자 화면에는 존재하지 않는 것과 같다
        String boardId = newBoard("ZZ검색비공개게시판");
        newPost(admin, boardId, "ZZ검색 숨은 글 " + KEYWORD, "본문");

        assertThat(postCount(KEYWORD, null)).as("비로그인에게 새면 안 된다").isZero();
        assertThat(postCount(KEYWORD, accessToken("user", "user1234!")))
                .as("일반 회원에게도 안 보인다").isZero();
        assertThat(postCount(KEYWORD, admin)).as("관리자는 모두 본다").isEqualTo(1);
    }

    @Test
    @DisplayName("비밀글은 검색 결과에 제목조차 나오지 않는다")
    void secretPostIsNotSearchable() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String postId = newPost(admin, NOTICE_BOARD, "ZZ검색 비밀글 " + KEYWORD, "본문");
        jdbc.update("UPDATE post SET secret_yn = 'Y' WHERE post_id = ?::integer", Integer.parseInt(postId));

        assertThat(postCount(KEYWORD, null)).isZero();
    }

    @Test
    @DisplayName("검색어의 LIKE 특수문자는 글자 그대로 찾는다(%로 전체조회 안 됨)")
    void likeWildcardIsEscaped() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        newPost(admin, NOTICE_BOARD, "ZZ검색 와일드카드 대상", "본문");

        assertThat(postCount("%", null)).as("'%'는 리터럴이어야 한다").isZero();
        assertThat(postCount("_", null)).as("'_'도 리터럴이어야 한다").isZero();
    }

    @Test
    @DisplayName("LIKE 특수문자가 든 검색어도 문맥 조각이 매칭 지점을 가리킨다")
    void snippetPointsAtMatchEvenWithSpecialChars() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String kw = "50%할인";
        // 앞쪽을 길게 채운다 — 스니펫이 본문 처음을 잘라오면 검색어가 안 들어간다
        String padding = "앞쪽채움".repeat(60);
        newPost(admin, NOTICE_BOARD, "ZZ검색 특수문자", padding + " " + kw + " 뒤쪽 문장");

        List<String> snippets = JsonPath.read(searchBody(kw, null), "$.data.posts[*].searchSnippet");
        assertThat(snippets).hasSize(1);
        // ★ 위치 계산(POSITION)에 이스케이프한 값을 쓰면 못 찾아서 0이 되고, 본문 맨 앞이 잘려 나온다.
        //    원문과 이스케이프본을 따로 넘겨야 하는 이유가 이것이다.
        assertThat(snippets.get(0)).as("스니펫이 매칭 지점 주변이어야 한다").contains(kw);
    }

    @Test
    @DisplayName("건수는 목록 길이가 아니라 실제 전체 건수다")
    void countIsNotCappedByPreviewSize() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        for (int i = 0; i < 12; i++) {
            newPost(admin, NOTICE_BOARD, "ZZ검색 다건 " + i + " " + KEYWORD, "본문");
        }

        String body = searchBody(KEYWORD, null);
        // 목록은 10건까지만 오지만 건수는 12여야 한다 — 길이를 쓰면 화면이 "10"으로 속인다
        List<String> titles = JsonPath.read(body, "$.data.posts[*].title");
        assertThat(titles).hasSize(10);
        assertThat((int) JsonPath.read(body, "$.data.postCount")).isEqualTo(12);
    }

    @Test
    @DisplayName("공백뿐인 검색어로 전체가 걸리지 않는다")
    void blankKeywordReturnsNothing() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        newPost(admin, NOTICE_BOARD, "ZZ검색 빈검색어 " + KEYWORD, "본문");

        // 완전히 빈 값은 컨트롤러가 400으로 막는다(EngagementTest). 공백만 있는 값은 여기까지 오므로
        // 서비스가 trim 후 빈 검색으로 보고 아무것도 조회하지 않아야 한다 — 안 그러면 전체 스캔이다.
        assertThat(postCount("   ", null)).isZero();
    }

    @Test
    @DisplayName("안내페이지도 함께 검색된다(메뉴에 연결된 것만)")
    void searchesPages() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        jdbc.update("INSERT INTO page (title, content, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (?, ?, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')",
                "ZZ검색 페이지 " + KEYWORD, "본문");

        // GEN 메뉴에 연결돼 있지 않다 → 사용자에겐 안 보이고, 관리자에겐 보인다
        assertThat((int) JsonPath.read(searchBody(KEYWORD, null), "$.data.pageCount")).isZero();
        assertThat((int) JsonPath.read(searchBody(KEYWORD, admin), "$.data.pageCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("게시판 목록 검색이 본문·작성자로도 된다")
    void boardListSearchByContentAndWriter() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        newPost(admin, NOTICE_BOARD, "ZZ검색 본문검색용", "여기에만 " + KEYWORD + " 가 있다");

        assertThat(boardListCount(KEYWORD, "title")).as("제목엔 없다").isZero();
        assertThat(boardListCount(KEYWORD, "content")).isEqualTo(1);
        assertThat(boardListCount(KEYWORD, "both")).isEqualTo(1);
        String nickname = jdbc.queryForObject(
                "SELECT nickname FROM member WHERE member_id = 'admin'", String.class);
        assertThat(boardListCount(nickname, "writer")).isPositive();
    }

    @Test
    @DisplayName("게시판 목록 검색도 '%'를 글자 그대로 찾는다")
    void boardListSearchEscapesWildcard() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        newPost(admin, NOTICE_BOARD, "ZZ검색 와일드카드", "본문");

        assertThat(boardListCount("%", "both"))
                .as("이스케이프가 빠지면 게시판 전체 글이 나온다").isZero();
    }

    // ===== helpers =====

    private String searchBody(String keyword, String token) throws Exception {
        return post("/api/adm/search/selectSearchAll.do",
                "{\"filterKeyword\":\"" + keyword + "\"}", token).body();
    }

    private int postCount(String keyword, String token) throws Exception {
        return JsonPath.read(searchBody(keyword, token), "$.data.postCount");
    }

    private int boardListCount(String keyword, String field) throws Exception {
        String body = post("/api/adm/post/selectPostList.do",
                "{\"boardId\":\"" + NOTICE_BOARD + "\",\"filterKeyword\":\"" + keyword + "\","
                        + "\"filterField\":\"" + field + "\"}", null).body();
        return JsonPath.read(body, "$.data.totalCount");
    }

    private String newPost(String token, String boardId, String title, String content) throws Exception {
        String postId = JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + boardId + "\",\"title\":\"" + title + "\","
                        + "\"content\":\"" + content + "\"}", token).body(), "$.data");
        return postId;
    }

    /** 취미를 만들면 게시판이 딸려 온다(커뮤니티 공개 — GEN 메뉴 없이도 열람 가능) */
    private String newHobbyBoard(String token, String name) throws Exception {
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"" + name + "\",\"summary\":\"검색 테스트\"}", token).body(), "$.data");
        return jdbc.queryForObject(
                "SELECT board_id::text FROM hobby WHERE hobby_id = ?::integer", String.class, hobbyId);
    }

    /**
     * 취미도 아니고 GEN 메뉴도 없는 게시판(테스트 전제 데이터라 DB에 직접 넣는다).
     * 사용자 화면에서는 존재하지 않는 것과 같다 — 검색이 그걸 지키는지 보려는 것이다.
     */
    private String newBoard(String name) {
        jdbc.update("INSERT INTO board (name, type_cd, description, list_cnt, file_yn, file_cnt_limit,"
                + " file_size_limit_mb, notice_yn, new_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (?, 'BOARD01', '검색 테스트', 10, 'N', 5, 10, 'N', 0, 'Y',"
                + " 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", name);
        return jdbc.queryForObject("SELECT board_id::text FROM board WHERE name = ?", String.class, name);
    }
}
