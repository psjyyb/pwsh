package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게시판 정의(설정) 관리 — 등록·수정 라운드트립과 메뉴 연결용 콤보.
 *
 * <p>왜 이 테스트가 있나. CMS에서는 이름 일괄 변경 때 VO 필드와 매퍼 파라미터가 어긋나 <b>게시판 등록·수정이
 * 전부 500</b>이었고, 콤보 SQL은 컬럼이 빠져 문법 오류였다(CMS 1.2.26에서 고침). 이 저장소는 매핑이 맞았지만
 * 컴파일·부팅·조회 테스트로는 안 잡히는 종류라 같은 회귀를 막으려고 둔다.
 * 그래서 상태코드만이 아니라 <b>저장한 값이 그대로 돌아오는지</b>를 본다.
 */
class BoardTest extends IntegrationTest {

    private static final String NAME = "ZZ게시판용량";

    @Test
    @DisplayName("게시판 등록·수정 시 첨부 용량 제한이 저장되고 목록·상세로 돌아온다")
    void boardCrudKeepsFileSizeLimit() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        assertThat(post("/api/adm/board/insertBoard.do",
                "{\"boardName\":\"" + NAME + "\",\"typeCd\":\"BOARD01\",\"fileYn\":\"Y\",\"fileSizeLimitMb\":\"25\"}",
                admin).statusCode()).isEqualTo(200);
        String id = jdbc.queryForObject(
                "SELECT board_id::text FROM board WHERE name = ? AND use_yn = 'Y'", String.class, NAME);
        assertThat(jdbc.queryForObject("SELECT file_size_limit_mb FROM board WHERE board_id = ?::integer",
                Integer.class, id)).as("기본값 10이 아니라 입력값으로 저장돼야 한다").isEqualTo(25);

        HttpResponse<String> view = post("/api/adm/board/selectBoardView.do", "{\"rowId\":\"" + id + "\"}", admin);
        assertThat((String) JsonPath.read(view.body(), "$.data.fileSizeLimitMb")).isEqualTo("25");

        assertThat(post("/api/adm/board/updateBoard.do",
                "{\"rowId\":\"" + id + "\",\"boardName\":\"" + NAME + "\",\"typeCd\":\"BOARD01\","
                        + "\"fileYn\":\"Y\",\"fileSizeLimitMb\":\"30\"}",
                admin).statusCode()).isEqualTo(200);
        List<String> listed = JsonPath.read(
                post("/api/adm/board/selectBoardList.do", "{\"filterKeyword\":\"" + NAME + "\"}", admin).body(),
                "$.data.list[*].fileSizeLimitMb");
        assertThat(listed).containsExactly("30");

        assertThat(post("/api/adm/board/deleteBoard.do", "{\"rowId\":\"" + id + "\"}", admin).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("게시판 콤보는 관리자에게 게시판 이름과 함께 나온다(메뉴 연결·취미 관리)")
    void comboListReturnsBoardNames() throws Exception {
        HttpResponse<String> r = post("/api/adm/board/selectBoardListCombo.do", "{}",
                accessToken("admin", "admin1234!"));
        assertThat(r.statusCode()).isEqualTo(200);
        List<String> names = JsonPath.read(r.body(), "$.data[*].boardName");
        assertThat(names).contains("공지사항", "1:1문의").doesNotContainNull();
    }

    @Test
    @DisplayName("게시판 콤보는 관리자 전용이다(비로그인 401, 일반 회원 403)")
    void comboListIsAdminOnly() throws Exception {
        // 관리자 메뉴관리·취미 관리 화면만 쓴다. 열어 두면 비공개 게시판의 존재·이름까지 누구나 훑을 수 있다
        assertThat(post("/api/adm/board/selectBoardListCombo.do", "{}", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/adm/board/selectBoardListCombo.do", "{}",
                accessToken("user", "user1234!")).statusCode()).isEqualTo(403);
    }
}
