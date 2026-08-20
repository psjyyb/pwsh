package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
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
        assertOk("/api/adm/policy/selectPolicyList.do");
        assertOk("/api/adm/page/selectPageList.do");
        assertOk("/api/adm/config/selectConfigView.do");
        assertOk("/api/adm/authgroup/selectAuthGroupList.do");
        assertOk("/api/adm/board/selectBoardList.do");
        assertOk("/api/adm/eventlog/selectEventlogList.do");
    }

    @Test
    @DisplayName("관리자 메뉴 관리 트리(권한필터 없이 전체)도 조회된다")
    void adminManageTree() throws Exception {
        HttpResponse<String> r = post("/api/adm/menu/selectMenuListManageTree.do",
                "{\"area\":\"ADM\"}", accessToken("admin", "admin1234!"));
        assertThat(r.statusCode()).isEqualTo(200);
    }
}
