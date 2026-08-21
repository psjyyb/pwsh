package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 게시글 소유자 검사 — 남의 글 수정/삭제 불가(403), 본인 글은 가능. board1(공개, 회원 접근 가능). */
class PostOwnershipTest extends IntegrationTest {

    private String insertPost(String title, String token) throws Exception {
        return JsonPath.read(
                post("/api/adm/post/insertPost.do", "{\"boardId\":\"1\",\"title\":\"" + title + "\",\"content\":\"x\"}", token)
                        .body(), "$.data");
    }

    @Test
    @DisplayName("회원은 남의 글을 수정/삭제할 수 없다(403)")
    void memberCannotModifyOthersPost() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");
        String postId = insertPost("adminpost", admin);

        assertThat(post("/api/adm/post/updatePost.do", "{\"rowId\":\"" + postId + "\",\"title\":\"hack\"}", user).statusCode())
                .isEqualTo(403);
        assertThat(post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + postId + "\"}", user).statusCode())
                .isEqualTo(403);
        // 작성자(관리자)는 삭제 가능
        assertThat(post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + postId + "\"}", admin).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("회원은 본인 글을 삭제할 수 있다")
    void memberCanManageOwnPost() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = insertPost("userpost", user);
        assertThat(post("/api/adm/post/deletePost.do", "{\"rowId\":\"" + postId + "\"}", user).statusCode())
                .isEqualTo(200);
    }
}
