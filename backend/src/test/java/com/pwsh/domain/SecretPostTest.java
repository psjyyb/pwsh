package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 비밀글 비밀번호.
 *
 * <p>★ 비밀번호는 <b>해시로만</b> 저장한다. 평문이면 DB가 한 번 새는 순간 비밀글이 전부 열리고,
 * 사람들은 다른 곳 비밀번호를 그대로 쓰는 경우가 많아 피해가 게시판 밖으로 번진다.
 *
 * <p>조용히 틀릴 수 있는 곳이 셋이다 — 평문으로 다시 저장되는 것, 수정 때 빈칸이 기존 해시를
 * 덮어 글이 영영 안 열리는 것, 해시로 바꾸기 전 평문으로 저장된 기존 글이 안 열리는 것.
 */
class SecretPostTest extends IntegrationTest {

    private static final String MIGRATION = "db/migration/V4__post_password_hash.sql";

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM post WHERE title LIKE 'ZZ비밀%'");
    }

    @Test
    @DisplayName("비밀글 비밀번호는 평문이 아니라 해시로 저장된다")
    void passwordIsStoredHashed() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = write(user, "ZZ비밀 저장", "pw1234");

        String stored = storedPassword(postId);
        assertThat(stored).as("DB가 새면 비밀글이 전부 열린다").isNotEqualTo("pw1234");
        assertThat(stored).startsWith("{bcrypt}$2");
    }

    @Test
    @DisplayName("맞는 비밀번호면 열리고 틀리면 잠긴다")
    void correctPasswordOpensWrongLocks() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = write(user, "ZZ비밀 열람", "pw1234");

        // 작성자·관리자는 비번 없이 열리므로 비로그인으로 확인한다
        assertThat(isLocked(view(postId, "pw1234"))).isFalse();
        assertThat(isLocked(view(postId, "wrong"))).isTrue();
        assertThat(isLocked(view(postId, null))).isTrue();
    }

    @Test
    @DisplayName("응답에 비밀번호(해시 포함)가 실리지 않는다")
    void passwordNotInResponse() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = write(user, "ZZ비밀 응답", "pw1234");

        String body = view(postId, "pw1234");
        assertThat(body).doesNotContain("\"password\"").doesNotContain("bcrypt");
    }

    @Test
    @DisplayName("수정 때 비밀번호를 비우면 기존 비밀번호가 유지되고, 새로 넣으면 바뀐다")
    void updateKeepsOrReplacesPassword() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = write(user, "ZZ비밀 수정", "pw1234");
        String before = storedPassword(postId);

        assertThat(update(user, postId, "").statusCode()).isEqualTo(200);
        // 빈칸을 인코딩해 덮어쓰면 아무도 모르는 비번이 돼 글이 영영 안 열린다
        assertThat(storedPassword(postId)).isEqualTo(before);
        assertThat(isLocked(view(postId, "pw1234"))).isFalse();

        assertThat(update(user, postId, "newpw99").statusCode()).isEqualTo(200);
        assertThat(storedPassword(postId)).startsWith("{bcrypt}");
        assertThat(isLocked(view(postId, "newpw99"))).isFalse();
        assertThat(isLocked(view(postId, "pw1234"))).isTrue();
    }

    @Test
    @DisplayName("해시 전환 전 평문으로 저장된 글도 마이그레이션 뒤 같은 비밀번호로 열린다")
    void legacyPlainPasswordMigrates() throws Exception {
        String user = accessToken("user", "user1234!");
        String postId = write(user, "ZZ비밀 기존글", "dummy");
        // 1.2.18 이전에 저장된 상태를 재현한다
        jdbc.update("UPDATE post SET password = 'legacy12' WHERE post_id = ?::integer", Integer.parseInt(postId));

        // 마이그레이션 SQL을 그대로 다시 실행한다 — 이미 해시인 행은 건드리지 않아야 하므로 두 번 돌려도 같아야 한다
        String sql = new String(new ClassPathResource(MIGRATION).getContentAsByteArray(), StandardCharsets.UTF_8);
        jdbc.execute(sql);
        String once = storedPassword(postId);
        jdbc.execute(sql);

        assertThat(once).startsWith("{bcrypt}$2");
        assertThat(storedPassword(postId)).as("해시를 다시 해시하면 글이 안 열린다").isEqualTo(once);
        // ★ DB(pgcrypto)가 만든 해시를 앱(Spring BCrypt)이 검증할 수 있어야 한다
        assertThat(isLocked(view(postId, "legacy12"))).isFalse();
        assertThat(isLocked(view(postId, "wrong"))).isTrue();
    }

    // ===== helpers =====

    private String write(String token, String title, String password) throws Exception {
        return JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"" + title + "\",\"content\":\"본문\","
                        + "\"secretYn\":\"Y\",\"password\":\"" + password + "\"}", token).body(), "$.data");
    }

    private java.net.http.HttpResponse<String> update(String token, String postId, String password) throws Exception {
        return post("/api/adm/post/updatePost.do",
                "{\"rowId\":\"" + postId + "\",\"boardId\":\"1\",\"title\":\"ZZ비밀 수정\",\"content\":\"본문\","
                        + "\"secretYn\":\"Y\",\"password\":\"" + password + "\"}", token);
    }

    private String view(String postId, String password) throws Exception {
        String pw = password == null ? "" : ",\"password\":\"" + password + "\"";
        return post("/api/adm/post/selectPostView.do",
                "{\"rowId\":\"" + postId + "\",\"viewUp\":\"N\"" + pw + "}", null).body();
    }

    private boolean isLocked(String body) {
        // 열린 글엔 secretLocked 키 자체가 없을 수 있어(null 생략) 문자열로 본다
        assertThat(body).contains("\"success\":true");
        return body.contains("\"secretLocked\":\"Y\"");
    }

    private String storedPassword(String postId) {
        return jdbc.queryForObject("SELECT password FROM post WHERE post_id = ?::integer",
                String.class, Integer.parseInt(postId));
    }
}
