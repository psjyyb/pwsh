package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 인앱 알림 보강(CMS 1.2.35) — 내 신청(폼) 처리상태 변경 알림 · 원래 동작이 커밋된 뒤에 만들고 민다.
 *
 * <p>댓글·멘션·모임 알림은 {@code MentionTest}·{@code RecruitFlowTest} 등이 본다.
 * <p>조용히 틀릴 수 있는 곳 — 메모만 고친 저장에도 "처리상태가 바뀌었다"가 오는 것, 모임 알림을 끈 회원이 신청 결과까지 못 받는 것,
 * 그리고 실시간 이벤트를 받고 바로 다시 읽었는데 알림이 아직 안 보이는 것(커밋 전에 밀면 그렇다).
 */
class NotificationTest extends IntegrationTest {

    private static final String PW = "Test1234!@";

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM form_answer_value WHERE form_answer_id IN (SELECT form_answer_id FROM form_answer"
                + " WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ알림%'))");
        jdbc.update("DELETE FROM form_answer WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ알림%')");
        jdbc.update("DELETE FROM form_field WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ알림%')");
        jdbc.update("DELETE FROM auth WHERE menu_id IN (SELECT menu_id FROM menu WHERE name = 'ZZ알림 신청메뉴')");
        jdbc.update("DELETE FROM menu WHERE name = 'ZZ알림 신청메뉴'");
        jdbc.update("DELETE FROM form WHERE title LIKE 'ZZ알림%'");
        jdbc.update("DELETE FROM notification WHERE content LIKE 'ZZ정리%'");
        jdbc.update("DELETE FROM batch_log WHERE job_cd = 'NOTIFICATION_PURGE'");
    }

    @Test
    @DisplayName("로그인해 낸 신청의 처리상태가 바뀌면 신청자에게 알림 — 모임 알림을 꺼도 온다, 메모만 고친 저장에는 오지 않는다")
    void formStatusNotifiesSubmitter() throws Exception {
        assertThat(signup("ntform", "알림신청자", "ntform@test.local").statusCode()).isEqualTo(200);
        String owner = accessToken("ntform", PW);
        String admin = accessToken("admin", "admin1234!");
        // 설정에 없는 유형이 '모임 알림'으로 떨어지면 신청 결과까지 막힌다 — 전부 꺼 둔다
        assertThat(post("/api/adm/notification/updateNotificationSetting.do",
                "{\"applyYn\":\"N\",\"commentYn\":\"N\",\"messageYn\":\"N\",\"reviewYn\":\"N\"}", owner).statusCode()).isEqualTo(200);

        String[] ids = newForm(admin);
        HttpResponse<String> submit = post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + ids[0] + "\",\"values\":{\"" + ids[1] + "\":\"신청합니다\"}}", owner);
        assertThat(submit.statusCode()).as(submit.body()).isEqualTo(200);
        String answerId = JsonPath.read(submit.body(), "$.data");

        assertThat(post("/api/adm/formanswer/updateFormAnswerStatus.do",
                "{\"rowId\":\"" + answerId + "\",\"statusCd\":\"ANSWER03\",\"adminMemo\":\"승인\"}", admin).statusCode()).isEqualTo(200);
        List<String> contents = JsonPath.read(list(owner), "$.data[*].content");
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0)).contains("ZZ알림 신청").contains("처리상태");
        assertThat(jdbc.queryForObject("SELECT type FROM notification WHERE member_id = 'ntform' ORDER BY notification_id DESC LIMIT 1",
                String.class)).isEqualTo("FORM");

        assertThat(post("/api/adm/formanswer/updateFormAnswerStatus.do",
                "{\"rowId\":\"" + answerId + "\",\"statusCd\":\"ANSWER03\",\"adminMemo\":\"메모만 수정\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(unread(owner)).as("상태가 그대로면 알리지 않는다").isEqualTo(1);
    }

    @Test
    @DisplayName("실시간 연결이 열려 있으면 알림이 생길 때 이벤트가 오고, 받자마자 다시 읽어도 이미 보인다(커밋 뒤에 민다)")
    void streamPushesAfterCommit() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"알림취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        String boardId = jdbc.queryForObject("SELECT board_id::text FROM hobby WHERE hobby_id = ?::integer", String.class, hobbyId);
        assertThat(signup("ntowner", "알림글쓴이", "ntowner@test.local").statusCode()).isEqualTo(200);
        assertThat(signup("ntother", "알림댓글러", "ntother@test.local").statusCode()).isEqualTo(200);
        String owner = accessToken("ntowner", PW);
        String other = accessToken("ntother", PW);
        String postId = JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"" + boardId + "\",\"title\":\"알림 실시간 글\",\"content\":\"본문\"}", owner).body(), "$.data");

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/adm/message/selectMessageListStream.do"))
                .header("Authorization", "Bearer " + owner)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build();
        HttpResponse<Stream<String>> res = http.send(req, HttpResponse.BodyHandlers.ofLines());
        assertThat(res.statusCode()).isEqualTo(200);
        // 앞단 프록시(nginx)가 응답을 모아 두면 이벤트가 버퍼가 찰 때까지 안 간다 — 이 응답만 버퍼링하지 말라고 알린다(CMS 1.2.36)
        assertThat(res.headers().firstValue("X-Accel-Buffering")).hasValue("no");
        Iterator<String> lines = res.body().iterator();
        try {
            assertThat(awaitEvent(lines, "ready")).as("연결 직후 ready").isTrue();
            assertThat(post("/api/adm/comment/insertComment.do",
                    "{\"postId\":\"" + postId + "\",\"content\":\"실시간 댓글\"}", other).statusCode()).isEqualTo(200);
            assertThat(awaitEvent(lines, "notification")).as("댓글이 달리면 바로 notification 이벤트").isTrue();
            assertThat(unread(owner)).as("이벤트를 받고 다시 읽으면 이미 보인다").isEqualTo(1);
        } finally {
            res.body().close();
        }
    }

    @Test
    @DisplayName("모임 단체 대화 스트림도 버퍼링하지 말라고 알린다(스트림이 둘이라 하나만 고치면 대화가 늦게 온다)")
    void chatStreamUnbuffered() throws Exception {
        String user = accessToken("user", "user1234!");
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/adm/recruitChat/selectRecruitChatListStream.do"))
                .header("Authorization", "Bearer " + user)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build();
        HttpResponse<Stream<String>> res = http.send(req, HttpResponse.BodyHandlers.ofLines());
        try {
            assertThat(res.statusCode()).isEqualTo(200);
            assertThat(res.headers().firstValue("X-Accel-Buffering")).hasValue("no");
        } finally {
            res.body().close();
        }
    }

    @Test
    @DisplayName("알림 정리 배치: 만든 지 90일 지난 알림은 읽음 여부와 상관없이 지우고, 그 안의 것은 남긴다")
    void purgeRemovesOldNotifications() throws Exception {
        insertNotification("user", "ZZ정리 91일 안읽음", "N", 91);
        insertNotification("user", "ZZ정리 91일 읽음", "Y", 91);
        insertNotification("user", "ZZ정리 89일", "N", 89);
        Integer jobId = jdbc.queryForObject("SELECT batch_job_id FROM batch_job WHERE job_cd = 'NOTIFICATION_PURGE'", Integer.class);

        assertThat(post("/api/adm/batch/updateBatchRun.do", "{\"rowId\":\"" + jobId + "\"}", accessToken("admin", "admin1234!"))
                .statusCode()).isEqualTo(200);
        Map<String, Object> log = awaitFinished("NOTIFICATION_PURGE");
        assertThat(log.get("status_cd")).isEqualTo("BATCH02");
        assertThat((String) log.get("result_msg")).contains("알림 2건 삭제");
        assertThat(jdbc.queryForList("SELECT content FROM notification WHERE content LIKE 'ZZ정리%'", String.class))
                .containsExactly("ZZ정리 89일");
    }

    // ===== helpers =====

    private void insertNotification(String memberId, String content, String readYn, int daysAgo) {
        jdbc.update("INSERT INTO notification (member_id, type, content, read_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (?, 'COMMENT', ?, ?, 'Y', 'system', 'system', NOW() - (? || ' days')::INTERVAL, NOW(), '127.0.0.1', '127.0.0.1')",
                memberId, content, readYn, String.valueOf(daysAgo));
    }

    /** 백그라운드 실행이 끝날 때까지(실행 중이 아닐 때까지) 기다렸다가 마지막 이력을 돌려준다. */
    private Map<String, Object> awaitFinished(String jobCd) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM batch_log WHERE job_cd = ? ORDER BY batch_log_id DESC LIMIT 1", jobCd);
            if (!rows.isEmpty() && !"BATCH01".equals(rows.get(0).get("status_cd"))) {
                return rows.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError(jobCd + " 실행이 10초 안에 끝나지 않았다");
    }

    /** 다음 이벤트 이름이 나올 때까지 줄을 읽는다(5초 제한). 핑 주석(:ping)·data 줄은 건너뛴다 */
    private boolean awaitEvent(Iterator<String> lines, String name) throws Exception {
        CompletableFuture<Boolean> f = CompletableFuture.supplyAsync(() -> {
            while (lines.hasNext()) {
                String line = lines.next();
                if (line.equals("event:" + name) || line.equals("event: " + name)) {
                    return true;
                }
            }
            return false;
        });
        try {
            return f.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        }
    }

    private String list(String token) throws Exception {
        return post("/api/adm/notification/selectNotificationList.do", "{}", token).body();
    }

    private int unread(String token) throws Exception {
        return JsonPath.read(post("/api/adm/notification/selectNotificationListUnreadCnt.do", "{}", token).body(), "$.data");
    }

    /** 로그인 필요 폼 + 짧은 글 문항 하나 → [formId, fieldId] */
    private String[] newForm(String admin) throws Exception {
        assertThat(post("/api/adm/form/insertForm.do",
                "{\"title\":\"ZZ알림 신청\",\"typeCd\":\"FORM01\",\"loginYn\":\"Y\",\"multiYn\":\"Y\",\"resultMailYn\":\"N\","
                        + "\"fields\":[{\"label\":\"내용\",\"fieldCd\":\"FIELD01\",\"requiredYn\":\"N\"}]}", admin).statusCode())
                .isEqualTo(200);
        String formId = jdbc.queryForObject("SELECT form_id::text FROM form WHERE title = 'ZZ알림 신청'", String.class);
        String fieldId = jdbc.queryForObject("SELECT form_field_id::text FROM form_field WHERE form_id = ?::integer",
                String.class, formId);
        // 회원이 폼을 내려면 사용자 메뉴에 걸려 있고 MEMBER 권한이 있어야 한다(GenAccessGuard.checkForm)
        jdbc.update("INSERT INTO menu (p_menu_id, area, name, sort_no, conn_cd, conn_id, target_yn, use_yn,"
                + " reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (0, 'GEN', 'ZZ알림 신청메뉴', 99, 'MENU05', ?::integer, 'N', 'Y',"
                + " 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", Integer.parseInt(formId));
        jdbc.update("INSERT INTO auth (menu_id, conn_id, type, menu_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " SELECT menu_id, 'MEMBER', 'GRP', 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'"
                + " FROM menu WHERE name = 'ZZ알림 신청메뉴'");
        return new String[] {formId, fieldId};
    }
}
