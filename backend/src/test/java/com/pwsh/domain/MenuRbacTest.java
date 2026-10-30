package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #4 — 권한그룹(관리자/회원/비회원) 기반 GEN 메뉴 트리 필터.
 * 시드: GUEST=메뉴 20,21,22,23(공개) / MEMBER=GEN 전체(회원전용 '1:1문의' 포함).
 */
class MenuRbacTest extends IntegrationTest {

    private static final String TREE = "/api/adm/menu/selectMenuListTree.do";

    private List<String> menuNames(String token) throws Exception {
        HttpResponse<String> r = post(TREE, "{\"area\":\"GEN\"}", token);
        assertThat(r.statusCode()).isEqualTo(200);
        return JsonPath.read(r.body(), "$..menuName");
    }

    @Test
    @DisplayName("비회원 GEN 트리에는 공개 메뉴만 — '공지사항' 포함, 회원전용 '1:1문의' 제외")
    void anonymousSeesOnlyPublicMenus() throws Exception {
        List<String> names = menuNames(null);
        assertThat(names).contains("공지사항");
        assertThat(names).doesNotContain("1:1문의");
    }

    @Test
    @DisplayName("회원 GEN 트리에는 회원전용 메뉴('1:1문의')가 포함된다")
    void memberSeesMemberMenus() throws Exception {
        List<String> names = menuNames(accessToken("user", "user1234!"));
        assertThat(names).contains("1:1문의");
    }

    @Test
    @DisplayName("로그인(회원) 시 노출 메뉴 수가 비회원보다 많다")
    void memberTreeIsLargerThanGuest() throws Exception {
        int guest = menuNames(null).size();
        int member = menuNames(accessToken("user", "user1234!")).size();
        assertThat(member).isGreaterThan(guest);
    }

    @Test
    @DisplayName("권한그룹 메뉴 설정으로 새 사용자 메뉴를 비회원에게 공개하고 다시 거둘 수 있다")
    void newGenMenuCanBeGrantedToGuestGroup() throws Exception {
        // 새 GEN 메뉴는 권한 행이 없어 아무에게도 안 보인다. 권한그룹 설정 창이 관리 트리(ADM+GEN)로 이 메뉴를 보여 주고
        // 그룹-메뉴 저장으로 GUEST에 붙여야 공개된다(CMS는 1.2.26까지 설정 창이 ADM 메뉴만 보여 줘 화면으로는 공개할 수 없었다)
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/menu/insertMenu.do",
                "{\"menuName\":\"ZZ공개전환\",\"area\":\"GEN\",\"connCd\":\"MENU01\",\"linkUrl\":\"/gen/zzgrant\"}",
                admin).statusCode()).isEqualTo(200);
        List<String> ids = JsonPath.read(
                post("/api/adm/menu/selectMenuListManageTree.do", "{\"area\":\"GEN\"}", admin).body(),
                "$.data[?(@.menuName=='ZZ공개전환')].rowId");
        assertThat(ids).as("설정 창이 부르는 관리 트리(GEN)에 새 메뉴가 나와야 한다").hasSize(1);
        String id = ids.get(0);
        assertThat(menuNames(null)).as("권한 행이 없으면 비공개").doesNotContain("ZZ공개전환");

        List<String> before = JsonPath.read(
                post("/api/adm/authgroup/selectAuthGroupListMenu.do", "{\"authGroupId\":\"GUEST\"}", admin).body(),
                "$.data");
        try {
            List<String> granted = new ArrayList<>(before);
            granted.add(id);
            saveGroupMenus(admin, "GUEST", granted);
            assertThat(menuNames(null)).contains("ZZ공개전환");

            saveGroupMenus(admin, "GUEST", before);
            assertThat(menuNames(null)).doesNotContain("ZZ공개전환");
        } finally {
            // 다른 테스트가 시드의 GUEST 권한을 전제로 하므로 실패해도 되돌린다
            saveGroupMenus(admin, "GUEST", before);
            post("/api/adm/menu/deleteMenu.do", "{\"rowId\":\"" + id + "\"}", admin);
        }
    }

    private void saveGroupMenus(String admin, String groupId, List<String> menuIds) throws Exception {
        String arr = menuIds.stream().map(m -> "\"" + m + "\"").collect(Collectors.joining(",", "[", "]"));
        assertThat(post("/api/adm/authgroup/updateAuthGroupMenu.do",
                "{\"authGroupId\":\"" + groupId + "\",\"menuIds\":" + arr + "}", admin).statusCode()).isEqualTo(200);
    }
}
