package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 그동안 자동 테스트가 없던 네 영역: 북마크 · 좋아요 · 통합검색 · 대시보드 통계.
 *
 * <p>화면에서는 계속 쓰이는데 회귀를 잡아 줄 게 없던 곳들이라, 각 기능의 "틀리면 사용자가 바로
 * 알아채는" 성질만 고정한다 — 토글의 두 번째 호출이 해제인지, 남의 것이 내 목록에 섞이지 않는지,
 * 검색이 권한 없는 글을 흘리지 않는지, 통계가 관리자에게만 열리는지.
 */
class EngagementTest extends IntegrationTest {

    /** 취미(전용 게시판 자동 생성) + 그 게시판에 글 하나 만들고 [bbsinfoId, bbsId] 반환. */
    private String[] hobbyWithPost(String hobbyNm, String title, String admin) throws Exception {
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"" + hobbyNm + "\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);
        String bbsinfoId = jdbc.queryForObject(
                "SELECT bbsinfo_id::text FROM t_hobby WHERE hobby_id = ?::integer", String.class, hobbyId);
        String bbsId = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + bbsinfoId + "\",\"title\":\"" + title + "\",\"context\":\"본문\"}",
                admin).body(), "$.data");
        return new String[] { hobbyId, bbsinfoId, bbsId };
    }

    @Test
    @DisplayName("북마크: 토글로 켜고 끄며, 내 목록에는 내 것만 담긴다")
    void bookmark_toggle_and_my_list() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertEquals(200, signup("bmuser", "북마크회원", "bmuser@test.local").statusCode());
        String me = accessToken("bmuser", "Test1234!@");

        String[] ids = hobbyWithPost("북마크취미", "북마크할 글", admin);
        String bbsId = ids[2];
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + ids[0] + "\",\"title\":\"북마크할 모집\",\"meetDt\":\""
                        + LocalDate.now().plusDays(4) + "\"}", admin).body(), "$.data");

        // 첫 토글 = 북마크됨
        assertEquals("Y", JsonPath.read(post("/api/adm/bookmark/updateBookmarkToggle.do",
                "{\"targetType\":\"BBS\",\"targetId\":\"" + bbsId + "\"}", me).body(), "$.data.markedYn"));
        assertEquals("Y", JsonPath.read(post("/api/adm/bookmark/updateBookmarkToggle.do",
                "{\"targetType\":\"RECRUIT\",\"targetId\":\"" + rid + "\"}", me).body(), "$.data.markedYn"));

        // 유형별 목록에 제목이 실려 온다(마이페이지가 이 값으로 링크를 그린다)
        String bbsList = post("/api/adm/bookmark/selectBookmarkList.do", "{\"targetType\":\"BBS\"}", me).body();
        assertTrue(bbsList.contains("북마크할 글"), "내 BBS 북마크 목록");
        List<Object> ids2 = JsonPath.read(
                post("/api/adm/bookmark/selectBookmarkListIds.do", "{\"targetType\":\"RECRUIT\"}", me).body(),
                "$.data");
        assertTrue(ids2.contains(rid), "Ids 변형은 id 목록만 준다");

        // 같은 대상을 다시 토글하면 해제
        assertEquals("N", JsonPath.read(post("/api/adm/bookmark/updateBookmarkToggle.do",
                "{\"targetType\":\"BBS\",\"targetId\":\"" + bbsId + "\"}", me).body(), "$.data.markedYn"));
        assertTrue(!post("/api/adm/bookmark/selectBookmarkList.do", "{\"targetType\":\"BBS\"}", me)
                .body().contains("북마크할 글"), "해제하면 목록에서 빠진다");

        // 남의 북마크는 내 목록에 없다
        assertEquals(200, signup("bmother", "다른회원", "bmother@test.local").statusCode());
        String other = accessToken("bmother", "Test1234!@");
        assertTrue(!post("/api/adm/bookmark/selectBookmarkList.do", "{\"targetType\":\"RECRUIT\"}", other)
                .body().contains("북마크할 모집"), "남의 북마크는 안 보인다");

        // 비로그인은 토글 불가
        assertNotEquals(200, post("/api/adm/bookmark/updateBookmarkToggle.do",
                "{\"targetType\":\"BBS\",\"targetId\":\"" + bbsId + "\"}", null).statusCode());
    }

    @Test
    @DisplayName("좋아요: 토글로 카운트가 오르내리고, 잘못된 대상은 막힌다")
    void like_toggle_updates_count() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertEquals(200, signup("lkuser", "좋아요회원", "lkuser@test.local").statusCode());
        String me = accessToken("lkuser", "Test1234!@");
        String bbsId = hobbyWithPost("좋아요취미", "좋아요 글", admin)[2];

        String on = post("/api/adm/like/toggleLike.do",
                "{\"targetType\":\"BBS\",\"targetId\":\"" + bbsId + "\"}", me).body();
        assertEquals("Y", JsonPath.read(on, "$.data.likedYn"));
        assertEquals("1", String.valueOf(JsonPath.<Object>read(on, "$.data.goodCnt")));
        assertEquals(1, jdbc.queryForObject("SELECT good_cnt FROM t_bbs WHERE bbs_id = ?::integer",
                Integer.class, bbsId), "글의 좋아요 수도 함께 반영된다");

        String off = post("/api/adm/like/toggleLike.do",
                "{\"targetType\":\"BBS\",\"targetId\":\"" + bbsId + "\"}", me).body();
        assertEquals("N", JsonPath.read(off, "$.data.likedYn"));
        assertEquals("0", String.valueOf(JsonPath.<Object>read(off, "$.data.goodCnt")));

        // 없는 글 / 지원하지 않는 유형 / 비로그인
        assertNotEquals(200, post("/api/adm/like/toggleLike.do",
                "{\"targetType\":\"BBS\",\"targetId\":\"99999999\"}", me).statusCode());
        assertEquals(400, post("/api/adm/like/toggleLike.do",
                "{\"targetType\":\"RECRUIT\",\"targetId\":\"" + bbsId + "\"}", me).statusCode());
        assertNotEquals(200, post("/api/adm/like/toggleLike.do",
                "{\"targetType\":\"BBS\",\"targetId\":\"" + bbsId + "\"}", null).statusCode());
    }

    @Test
    @DisplayName("통합검색: 취미·모집·글을 찾고, 게스트에게 비공개 게시판 글은 흘리지 않는다")
    void search_all_respects_access() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String uniq = "검색용키워드ZZ";
        String[] ids = hobbyWithPost("검색취미" + uniq, "공개글 " + uniq, admin);
        assertEquals(200, post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + ids[0] + "\",\"title\":\"모집 " + uniq + "\",\"meetDt\":\""
                        + LocalDate.now().plusDays(7) + "\"}", admin).statusCode());

        // 게스트가 못 보는 게시판(취미 미연결 + GUEST 메뉴권한 없음)에 같은 키워드로 글 하나
        String closedBoard = jdbc.queryForObject(
                "SELECT bi.bbsinfo_id::text FROM t_bbsinfo bi WHERE bi.use_yn = 'Y'"
                        + " AND NOT EXISTS (SELECT 1 FROM t_hobby h WHERE h.bbsinfo_id = bi.bbsinfo_id)"
                        + " AND NOT EXISTS (SELECT 1 FROM t_menu m JOIN t_auth a ON a.menu_id = m.menu_id"
                        + "                  AND a.conn_id = 'GUEST' AND a.menu_yn = 'Y'"
                        + "                 WHERE m.conn_ty = 'MENU02' AND m.conn_id::text = bi.bbsinfo_id::text)"
                        + " ORDER BY bi.bbsinfo_id LIMIT 1", String.class);
        assertNotNull(closedBoard, "게스트 비공개 게시판이 시드에 있어야 한다");
        String secretId = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + closedBoard + "\",\"title\":\"비공개글 " + uniq + "\",\"context\":\"본문\"}",
                admin).body(), "$.data");

        String guest = post("/api/adm/search/selectSearchAll.do", "{\"filterKeyword\":\"" + uniq + "\"}", null).body();
        assertTrue(guest.contains("검색취미" + uniq), "취미가 검색된다");
        assertTrue(guest.contains("모집 " + uniq), "모집이 검색된다");
        assertTrue(guest.contains("공개글 " + uniq), "공개 게시판 글이 검색된다");
        assertTrue(!guest.contains("비공개글 " + uniq), "권한 없는 게시판 글은 검색 결과에도 나오지 않는다");

        // 검색어는 필수
        assertEquals(400, post("/api/adm/search/selectSearchAll.do", "{\"filterKeyword\":\"\"}", null).statusCode());
        // LIKE 와일드카드를 그대로 흘리면 전체가 걸린다 — 이스케이프되어 아무것도 안 나와야 한다
        String wild = post("/api/adm/search/selectSearchAll.do", "{\"filterKeyword\":\"%\"}", null).body();
        assertTrue(!wild.contains("공개글 " + uniq), "% 는 리터럴로 취급된다");

        post("/api/adm/bbs/deleteBbs.do", "{\"rowId\":\"" + secretId + "\"}", admin);
    }

    @Test
    @DisplayName("대시보드 통계: 관리자만 볼 수 있고 추이·합계가 채워진다")
    void stats_admin_only() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String body = post("/api/adm/stats/selectStatsList.do", "{}", admin).body();
        assertTrue(body.contains("\"days\""), "관리자 통계 응답: " + body);
        int days = JsonPath.read(body, "$.data.days");
        List<Object> daily = JsonPath.read(body, "$.data.daily");
        assertTrue(days > 0, "추이 기간");
        assertEquals(days, daily.size(), "추이는 기간만큼의 날짜 칸을 채워 준다(빈 날도 0으로)");
        assertNotNull(JsonPath.read(body, "$.data.totals.userCnt"));
        assertNotNull(JsonPath.read(body, "$.data.totals.reportPendingCnt"));

        String signupBody = signup("stuser", "통계회원", "stuser@test.local").body();
        assertTrue(signupBody.contains("\"success\":true"), "가입 응답: " + signupBody);
        String loginBody = login("stuser", "Test1234!@").body();
        assertTrue(loginBody.contains("accessToken"), "로그인 응답: " + loginBody);
        assertEquals(403, post("/api/adm/stats/selectStatsList.do", "{}",
                JsonPath.read(loginBody, "$.data.accessToken")).statusCode(), "일반 회원은 통계를 못 본다");
        assertEquals(401, post("/api/adm/stats/selectStatsList.do", "{}", null).statusCode());
    }
}
