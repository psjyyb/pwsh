package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 비로그인(게스트)이 공개 화면을 여는 데 필요한 조회 API가 전부 열려 있는지 검증.
 *
 * <p>화면 하나가 여러 API를 부르기 때문에, 그중 하나라도 401이면 프론트의 401 인터셉터가
 * 로그인 화면으로 튕겨 <b>페이지 전체를 못 보는</b> 상태가 된다(실제로 모집 화면에서 발생했다).
 * 그래서 개별 엔드포인트 단위로 게스트 접근을 고정한다.
 */
class GuestPublicPageTest extends IntegrationTest {

    @Test
    @DisplayName("모집 목록 화면이 부르는 API는 게스트도 200")
    void recruitPageApisOpenToGuest() throws Exception {
        // 목록 본체
        assertThat(post("/api/adm/recruit/selectRecruitList.do", "{\"pageNo\":1,\"pageSize\":10}", null).statusCode())
                .isEqualTo(200);
        // 취미 필터
        assertThat(post("/api/adm/hobby/selectHobbyList.do", "{\"pageNo\":1,\"pageSize\":100}", null).statusCode())
                .isEqualTo(200);
        // 지역 필터(공통코드 콤보) — 막히면 화면 진입 자체가 로그인으로 튕긴다
        assertThat(post("/api/adm/code/selectCodeListCombo.do", "{\"pCodeId\":\"AREA00\"}", null).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("레이아웃·메인이 부르는 API는 게스트도 200")
    void layoutApisOpenToGuest() throws Exception {
        assertThat(post("/api/adm/menu/selectMenuListTree.do", "{\"area\":\"GEN\"}", null).statusCode()).isEqualTo(200);
        assertThat(post("/api/adm/config/selectConfigView.do", "{}", null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("공개 게시글 상세 화면이 부르는 API는 게스트도 200")
    void postViewApisOpenToGuest() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String bbsinfoId = firstHobbyBoardId();
        String bbsId = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + bbsinfoId + "\",\"title\":\"게스트공개글\",\"context\":\"본문\"}",
                admin).body(), "$.data");

        assertThat(post("/api/adm/bbs/selectBbsView.do", "{\"rowId\":\"" + bbsId + "\"}", null).statusCode())
                .isEqualTo(200);
        assertThat(post("/api/adm/comment/selectCommentList.do", "{\"bbsId\":\"" + bbsId + "\"}", null).statusCode())
                .isEqualTo(200);
        // 첨부 목록 — 막히면 첨부 사용 게시판의 글을 게스트가 아예 못 연다
        assertThat(post("/api/adm/file/selectFileMapList.do",
                "{\"mapKey\":\"" + bbsId + "\",\"fileLoc\":\"BBS\"}", null).statusCode()).isEqualTo(200);

        post("/api/adm/bbs/deleteBbs.do", "{\"rowId\":\"" + bbsId + "\"}", admin);
    }

    @Test
    @DisplayName("게스트가 못 보는 게시판의 첨부 목록은 게스트에게 403")
    void fileMapListStillGuardedByPostPermission() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 취미 미연결 + GUEST 메뉴권한 없음 = 게스트가 못 보는 게시판(시드의 갤러리·1:1문의)
        String bbsinfoId = jdbc.queryForObject(
                "SELECT bi.bbsinfo_id::text FROM t_bbsinfo bi WHERE bi.use_yn = 'Y'"
                        + " AND NOT EXISTS (SELECT 1 FROM t_hobby h WHERE h.bbsinfo_id = bi.bbsinfo_id)"
                        + " AND NOT EXISTS (SELECT 1 FROM t_menu m JOIN t_auth a ON a.menu_id = m.menu_id"
                        + "                  AND a.conn_id = 'GUEST' AND a.menu_yn = 'Y'"
                        + "                 WHERE m.conn_ty = 'MENU02' AND m.conn_id::text = bi.bbsinfo_id::text)"
                        + " ORDER BY bi.bbsinfo_id LIMIT 1", String.class);
        assertThat(bbsinfoId).as("게스트 비공개 게시판이 시드에 있어야 한다").isNotNull();

        String bbsId = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + bbsinfoId + "\",\"title\":\"비공개글\",\"context\":\"본문\"}",
                admin).body(), "$.data");

        assertThat(post("/api/adm/file/selectFileMapList.do",
                "{\"mapKey\":\"" + bbsId + "\",\"fileLoc\":\"BBS\"}", null).statusCode())
                .as("권한 없는 게시판의 첨부는 파일명조차 노출하지 않는다").isEqualTo(403);

        post("/api/adm/bbs/deleteBbs.do", "{\"rowId\":\"" + bbsId + "\"}", admin);
    }

    /** 취미에 연결된(= 커뮤니티 공개) 게시판 하나. */
    private String firstHobbyBoardId() {
        return jdbc.queryForObject(
                "SELECT h.bbsinfo_id::text FROM t_hobby h WHERE h.use_yn = 'Y' AND h.bbsinfo_id IS NOT NULL"
                        + " ORDER BY h.hobby_id LIMIT 1", String.class);
    }

    @Test
    @DisplayName("설정 조회는 게스트·일반회원에게 사이트명·로고만 준다(보안 정책값 비노출)")
    void configViewExposesOnlyDisplayFieldsToNonAdmin() throws Exception {
        String guest = post("/api/adm/config/selectConfigView.do", "{}", null).body();
        assertThat(JsonPath.<String>read(guest, "$.data.title")).as("사이트명은 필요하다").isNotBlank();
        assertThat(guest).as("잠금 임계값·만료일 같은 정책값은 게스트에게 나가지 않는다")
                .doesNotContain("failCntLimit").doesNotContain("failCntDeniedTi")
                .doesNotContain("pwExpireCnt").doesNotContain("sessionExpireCnt")
                .doesNotContain("delLogCnt").doesNotContain("accIpYn");

        // 일반회원(관리자 아님)도 같다
        String member = post("/api/adm/config/selectConfigView.do", "{}",
                accessToken("user", "user1234!")).body();
        assertThat(member).doesNotContain("failCntLimit").doesNotContain("sessionExpireCnt");

        // 관리자는 환경설정 화면을 채워야 하므로 전체를 받는다
        String admin = post("/api/adm/config/selectConfigView.do", "{}",
                accessToken("admin", "admin1234!")).body();
        assertThat(admin).contains("failCntLimit").contains("sessionExpireCnt");
    }

    @Test
    @DisplayName("게시판 ID에 숫자가 아닌 값이 오면 500이 아니라 400")
    void nonNumericBoardIdIsRejected() throws Exception {
        assertThat(post("/api/adm/bbs/selectBbsList.do",
                "{\"bbsinfoId\":\"BBSINFO001\",\"pageNo\":1,\"pageSize\":10}",
                accessToken("admin", "admin1234!")).statusCode())
                .as("URL을 손으로 고쳐 들어와도 SQL 캐스트 에러가 새지 않는다").isEqualTo(400);
    }

    @Test
    @DisplayName("쓰기 API는 게스트에게 계속 막혀 있다")
    void writeApisStillBlockedForGuest() throws Exception {
        assertThat(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"1\",\"title\":\"게스트\"}", null).statusCode()).isNotEqualTo(200);
        assertThat(post("/api/adm/code/insertCode.do",
                "{\"rowId\":\"ZZGUEST\",\"pCodeId\":\"0\",\"codeNm\":\"x\"}", null).statusCode()).isNotEqualTo(200);
        // 코드 '목록'(관리자용)은 콤보와 달리 여전히 인증 필요
        assertThat(post("/api/adm/code/selectCodeList.do", "{\"pageNo\":1,\"pageSize\":10}", null).statusCode())
                .isNotEqualTo(200);
    }
}
