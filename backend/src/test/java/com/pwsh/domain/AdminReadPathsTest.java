package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 조회 경로 전수 — 각 도메인 목록/단건 조회가 실제 DB로 200을 반환하는지(select 매퍼·서비스·컨트롤러 커버).
 */
class AdminReadPathsTest extends IntegrationTest {

    private void assertOk(String url) throws Exception {
        HttpResponse<String> r = post(url, "{}", accessToken("admin", "admin1234!"));
        assertThat(r.statusCode()).as(url).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(r.body(), "$.success")).as(url).isTrue();
    }

    @Test
    @DisplayName("관리자 목록/조회 엔드포인트가 전부 200을 반환한다")
    void adminReadPaths() throws Exception {
        assertOk("/api/adm/code/selectCodeList.do");
        assertOk("/api/adm/menu/selectMenuList.do");
        assertOk("/api/adm/member/selectMemberList.do");
        assertOk("/api/adm/popup/selectPopupList.do");
        assertOk("/api/adm/banner/selectBannerList.do");
        assertOk("/api/adm/policy/selectPolicyList.do");
        assertOk("/api/adm/page/selectPageList.do");
        assertOk("/api/adm/config/selectConfigView.do");
        assertOk("/api/adm/authgroup/selectAuthGroupList.do");
        assertOk("/api/adm/board/selectBoardList.do");
        assertOk("/api/adm/eventlog/selectEventlogList.do");
        assertOk("/api/adm/mailtemplate/selectMailTemplateList.do");
        assertOk("/api/adm/maillog/selectMailLogList.do");
        assertOk("/api/adm/privacylog/selectPrivacylogList.do");
        assertOk("/api/adm/form/selectFormList.do");
        assertOk("/api/adm/formanswer/selectFormAnswerList.do");
    }

    @Test
    @DisplayName("회원 목록에 회원유형 이름이 나온다(코드값 대신)")
    void memberListShowsTypeName() throws Exception {
        // 매퍼 별칭이 VO 필드와 어긋나면 값이 조용히 버려지고 화면은 'MEM01' 같은 코드로 대신 보여 준다
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzmtype01");
        String expected = jdbc.queryForObject(
                "SELECT name FROM code WHERE code_id = 'MEM01' AND p_code_id = 'MEM00'", String.class);

        String body = post("/api/adm/member/selectMemberList.do", "{\"filterKeyword\":\"zzmtype01\"}", admin).body();
        assertThat((List<String>) JsonPath.read(body, "$.data.list[*].memberId")).containsExactly("zzmtype01");
        assertThat((List<String>) JsonPath.read(body, "$.data.list[*].memberTypeName")).containsExactly(expected);
    }

    @Test
    @DisplayName("관리자 메뉴 관리 트리(권한필터 없이 전체)도 조회된다")
    void adminManageTree() throws Exception {
        HttpResponse<String> r = post("/api/adm/menu/selectMenuListManageTree.do",
                "{\"area\":\"ADM\"}", accessToken("admin", "admin1234!"));
        assertThat(r.statusCode()).isEqualTo(200);
    }
}
