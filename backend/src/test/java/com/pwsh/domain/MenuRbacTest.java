package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
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
        return JsonPath.read(r.body(), "$..menuNm");
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
}
