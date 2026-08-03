package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B — GEN 콘텐츠 단위 접근제어(GenAccessGuard).
 * 시드: board1(메뉴21,GUEST+MEMBER=공개) / board4(메뉴24,MEMBER 전용) / board3(연결 메뉴 없음=관리자만).
 */
class GenAccessControlTest extends IntegrationTest {

    private static final String BBS_LIST = "/api/adm/bbs/selectBbsList.do";
    private static final String BBSINFO_VIEW = "/api/adm/bbsinfo/selectBbsinfoView.do";

    @Test
    @DisplayName("비회원은 공개 게시판(board1) 목록을 볼 수 있다")
    void guestCanReadPublicBoard() throws Exception {
        assertThat(post(BBS_LIST, "{\"bbsinfoId\":\"1\"}", null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("비회원은 회원전용 게시판(board4) 목록에 접근하면 403")
    void guestBlockedFromMemberOnlyBoard() throws Exception {
        assertThat(post(BBS_LIST, "{\"bbsinfoId\":\"4\"}", null).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("회원은 회원전용 게시판(board4)을 볼 수 있다")
    void memberCanReadMemberOnlyBoard() throws Exception {
        String token = accessToken("user", "user1234!");
        assertThat(post(BBS_LIST, "{\"bbsinfoId\":\"4\"}", token).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("관리자는 콘텐츠 인가를 우회(회원전용 board4도 접근)")
    void adminBypassesContentGuard() throws Exception {
        String token = accessToken("admin", "admin1234!");
        assertThat(post(BBS_LIST, "{\"bbsinfoId\":\"4\"}", token).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("비회원은 연결 메뉴 없는 게시판(board3)에 접근하면 403")
    void guestBlockedFromUnlinkedBoard() throws Exception {
        assertThat(post(BBS_LIST, "{\"bbsinfoId\":\"3\"}", null).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("비회원은 회원전용 게시판 상세(스킨) 조회 시 403")
    void guestBlockedFromMemberOnlyBoardInfo() throws Exception {
        assertThat(post(BBSINFO_VIEW, "{\"dbKey\":\"4\"}", null).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("비회원도 공개 게시판 상세는 조회 가능")
    void guestCanReadPublicBoardInfo() throws Exception {
        assertThat(post(BBSINFO_VIEW, "{\"dbKey\":\"1\"}", null).statusCode()).isEqualTo(200);
    }
}
