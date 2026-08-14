package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 취미 커뮤니티 핵심 흐름 통합테스트: 셀프가입 → 취미 → 모집 → 신청 → 수락 + RBAC + 취미레벨.
 * 실서버(RANDOM_PORT) + 실 PostgreSQL(pwsh_test) + 실 HTTP. 모킹 0.
 */
class RecruitFlowTest extends IntegrationTest {

    @Test
    void signup_recruit_apply_accept_and_level_flow() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 기본 시드 취미(등산/보드게임/낚시)가 있으므로, 신규 취미의 기대 노출순서 = 현재 최대값+1
        Integer maxOrdrBefore = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_ordr), 0) FROM t_hobby WHERE use_yn = 'Y'", Integer.class);

        // 취미 생성(관리자) → hobbyId
        HttpResponse<String> hres = post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"테스트취미\",\"summary\":\"소개\",\"difficultyCd\":\"HOBBYLV01\"}", admin);
        assertEquals(200, hres.statusCode());
        String hobbyId = JsonPath.read(hres.body(), "$.data");
        assertNotNull(hobbyId);

        // 셀프 회원가입(주최자/신청자) — 닉네임 + 이메일 인증코드 필수
        assertEquals(200, signup("org1", "주최왕", "org1@test.local").statusCode());
        assertEquals(200, signup("app1", "신청왕", "app1@test.local").statusCode());

        String orgTok = accessToken("org1", "Test1234!@");
        String appTok = accessToken("app1", "Test1234!@");

        // 취미 등록 시 전용 게시판 자동 생성·연결 확인
        HttpResponse<String> hv = post("/api/adm/hobby/selectHobbyView.do", "{\"rowId\":\"" + hobbyId + "\"}", null);
        String boardId = JsonPath.read(hv.body(), "$.data.bbsinfoId");
        assertNotNull(boardId);
        // 노출 순서 미지정 → 기존 최대값+1 자동 부여
        assertEquals(String.valueOf(maxOrdrBefore + 1), JsonPath.read(hv.body(), "$.data.sortOrdr"));
        // 취미 게시판은 공개: 비로그인 목록 조회 가능, 회원은 글 작성 가능
        assertEquals(200, post("/api/adm/bbs/selectBbsList.do",
                "{\"bbsinfoId\":\"" + boardId + "\",\"pageNo\":1,\"pageSize\":10}", null).statusCode());
        assertEquals(200, post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + boardId + "\",\"title\":\"첫 글\",\"context\":\"<p>안녕</p>\"}", appTok).statusCode());

        // 모집 등록(주최자)
        HttpResponse<String> rres = post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"주말 모임\",\"capacity\":\"4\",\"region\":\"서울\"}", orgTok);
        assertEquals(200, rres.statusCode());
        String recruitId = JsonPath.read(rres.body(), "$.data");

        // 공개 목록(비로그인) 조회 가능, 비로그인 등록은 401
        assertEquals(200, post("/api/adm/recruit/selectRecruitList.do", "{\"pageNo\":1,\"pageSize\":10}", null).statusCode());
        assertEquals(401, post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"무단\"}", null).statusCode());

        // 참여 신청(신청자), 본인 모집 신청은 차단
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + recruitId + "\",\"applyMemo\":\"가고 싶어요\"}", appTok).statusCode());
        assertNotEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + recruitId + "\"}", orgTok).statusCode());

        // 주최자 신청자 목록 → 1건, 신청자 닉네임 조인
        HttpResponse<String> ares = post("/api/adm/recruitApply/selectRecruitApplyList.do",
                "{\"recruitId\":\"" + recruitId + "\"}", orgTok);
        assertEquals(200, ares.statusCode());
        List<Object> applies = JsonPath.read(ares.body(), "$.data");
        assertEquals(1, applies.size());
        String applyId = JsonPath.read(ares.body(), "$.data[0].rowId");
        assertEquals("신청왕", JsonPath.read(ares.body(), "$.data[0].nickname"));

        // 수락 → 상세에서 수락수 1, 주최자 닉네임
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + applyId + "\",\"applyStatus\":\"APPLY02\"}", orgTok).statusCode());
        HttpResponse<String> vres = post("/api/adm/recruit/selectRecruitView.do",
                "{\"rowId\":\"" + recruitId + "\"}", null);
        assertEquals("1", JsonPath.read(vres.body(), "$.data.acceptedCnt"));
        assertEquals("주최왕", JsonPath.read(vres.body(), "$.data.regNm"));

        // 취미 레벨(신청자) 설정 → 목록 1건
        assertEquals(200, post("/api/adm/userHobby/insertUserHobby.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"levelCd\":\"HOBBYLV02\"}", appTok).statusCode());
        HttpResponse<String> lres = post("/api/adm/userHobby/selectUserHobbyList.do", "{}", appTok);
        List<Object> levels = JsonPath.read(lres.body(), "$.data");
        assertEquals(1, levels.size());
    }
}
