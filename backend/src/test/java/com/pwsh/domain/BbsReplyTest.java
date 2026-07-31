package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 답글(스레드) — 원글 게시판 상속 + depth+1 저장. board1(공개). */
class BbsReplyTest extends IntegrationTest {

    private String insertBbs(String body, String token) throws Exception {
        return JsonPath.read(post("/api/adm/bbs/insertBbs.do", body, token).body(), "$.data");
    }

    @Test
    @DisplayName("답글은 원글의 게시판을 상속하고 depth+1로 저장된다")
    void replyInheritsBoardAndDepth() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String root = insertBbs("{\"bbsinfoId\":\"1\",\"title\":\"원글\",\"context\":\"x\"}", admin);
        String reply = insertBbs("{\"bbsinfoId\":\"1\",\"pBbsId\":\"" + root + "\",\"title\":\"답글\",\"context\":\"y\"}", admin);

        HttpResponse<String> view = post("/api/adm/bbs/selectBbsView.do", "{\"dbKey\":\"" + reply + "\"}", admin);
        assertThat(view.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(view.body(), "$.data.bbsinfoId")).isEqualTo("1"); // 원글 게시판 상속
        assertThat((String) JsonPath.read(view.body(), "$.data.pBbsId")).isEqualTo(root);
        assertThat((String) JsonPath.read(view.body(), "$.data.bbsDepth")).isEqualTo("2"); // 원글1 → 답글2

        post("/api/adm/bbs/deleteBbs.do", "{\"dbKey\":\"" + reply + "\"}", admin);
        post("/api/adm/bbs/deleteBbs.do", "{\"dbKey\":\"" + root + "\"}", admin);
    }
}
