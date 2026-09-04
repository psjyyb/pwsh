package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 금칙어 검증.
 * - 등록된 금칙어가 게시글 제목·본문·댓글에 있으면 400으로 막히는지
 * - 대소문자·공백을 바꿔 우회하려 해도 막히는지
 * - 금칙어를 지우면(논리삭제) 다시 등록되는지 — 차단이 데이터 기준으로 동작하는지 확인
 * - 같은 단어 중복 등록이 거부되는지
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class BanwordTest extends IntegrationTest {

    @Test
    void banword_blocks_post_and_comment() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 금칙어 등록
        assertEquals(200, post("/api/adm/banword/insertBanword.do",
                "{\"word\":\"금칙테스트\"}", admin).statusCode());
        // 중복 등록은 거부 — pwsh의 ErrorCode.DUPLICATE 는 409(CONFLICT)로 매핑된다
        assertEquals(409, post("/api/adm/banword/insertBanword.do",
                "{\"word\":\"금칙테스트\"}", admin).statusCode());
        // 대소문자만 다른 중복도 거부(영문 단어로 확인)
        assertEquals(200, post("/api/adm/banword/insertBanword.do", "{\"word\":\"BadWord\"}", admin).statusCode());
        assertEquals(409, post("/api/adm/banword/insertBanword.do", "{\"word\":\"badword\"}", admin).statusCode());

        // 본문에 금칙어 → 차단
        assertEquals(400, post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"정상제목\",\"content\":\"여기에 금칙테스트 포함\"}", admin).statusCode());
        // 제목에 금칙어 → 차단
        assertEquals(400, post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"금칙테스트 제목\",\"content\":\"본문\"}", admin).statusCode());
        // 공백으로 우회 시도 → 차단(공백 제거 후 비교)
        assertEquals(400, post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"제목\",\"content\":\"금 칙 테 스 트\"}", admin).statusCode());
        // 대소문자 우회 시도 → 차단
        assertEquals(400, post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"제목\",\"content\":\"BADWORD 입니다\"}", admin).statusCode());

        // 금칙어 없는 글은 정상 등록
        String postId = JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"깨끗한 글\",\"content\":\"문제 없는 본문\"}", admin).body(), "$.data");
        assertTrue(postId != null && !postId.isEmpty());

        // 댓글도 같은 규칙 적용
        assertEquals(400, post("/api/adm/comment/insertComment.do",
                "{\"postId\":\"" + postId + "\",\"content\":\"금칙테스트 댓글\"}", admin).statusCode());
        assertEquals(200, post("/api/adm/comment/insertComment.do",
                "{\"postId\":\"" + postId + "\",\"content\":\"정상 댓글\"}", admin).statusCode());

        // 금칙어 삭제 후에는 통과 — 차단이 등록 데이터에 따라 움직인다
        List<String> ids = jdbc.queryForList(
                "SELECT banword_id::text FROM banword WHERE use_yn = 'Y' AND word IN ('금칙테스트','BadWord')",
                String.class);
        for (String id : ids) {
            assertEquals(200, post("/api/adm/banword/deleteBanword.do", "{\"rowId\":\"" + id + "\"}", admin).statusCode());
        }
        assertEquals(200, post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"제목\",\"content\":\"이제는 금칙테스트 허용\"}", admin).statusCode());

        // 정리
        jdbc.update("DELETE FROM banword WHERE word IN ('금칙테스트','BadWord','badword')");
    }
}
