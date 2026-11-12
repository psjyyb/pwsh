package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.batch.service.BatchService;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 배치 작업 관리 — 주기 변경 · 자동 실행 켜고 끄기 · 즉시 실행 · 실행 이력.
 *
 * <p>조용히 틀릴 수 있는 곳 — 화면의 주기만 바뀌고 스케줄러에는 옛 주기가 그대로 남는 것(재시작해야 반영),
 * 같은 작업이 겹쳐 도는 것(회원 라이프사이클이 두 번 돌면 안내메일이 두 번 나간다), 그리고 서버가 실행 도중
 * 내려가 이력이 영원히 "실행 중"으로 남는 것. 그래서 스케줄 등록 여부(scheduledYn)와 이력 상태를 함께 본다.
 */
class BatchJobTest extends IntegrationTest {

    /** CMS 5종(1.2.36 알림 정리 포함) + 이 저장소 전용 모집 하루 전 알림 */
    private static final List<String> JOBS =
            List.of("FILE_GC", "MAIL_LOG_PURGE", "MEMBER_LIFECYCLE", "LOG_RETENTION", "NOTIFICATION_PURGE", "RECRUIT_REMIND");

    @Autowired
    private BatchService batchService;

    @AfterEach
    void cleanup() {
        jdbc.update("UPDATE batch_job SET cron_expr = '0 0 4 * * *', auto_yn = 'Y' WHERE job_cd = 'FILE_GC'");
        jdbc.update("UPDATE batch_job SET cron_expr = '0 30 4 * * *', auto_yn = 'Y' WHERE job_cd = 'MAIL_LOG_PURGE'");
        jdbc.update("UPDATE batch_job SET cron_expr = '0 0 3 * * *', auto_yn = 'Y' WHERE job_cd = 'MEMBER_LIFECYCLE'");
        jdbc.update("UPDATE batch_job SET cron_expr = '0 30 3 * * *', auto_yn = 'Y' WHERE job_cd = 'LOG_RETENTION'");
        jdbc.update("UPDATE batch_job SET cron_expr = '0 0 9 * * *', auto_yn = 'Y' WHERE job_cd = 'RECRUIT_REMIND'");
        jdbc.update("UPDATE batch_job SET cron_expr = '0 0 5 * * *', auto_yn = 'Y' WHERE job_cd = 'NOTIFICATION_PURGE'");
        jdbc.update("DELETE FROM batch_job WHERE job_cd LIKE 'ZZ%'");
        jdbc.update("DELETE FROM batch_log");
        jdbc.update("DELETE FROM event_log WHERE target_table = 'zzbatch'");
        jdbc.update("DELETE FROM mail_log WHERE template_cd = 'ZZ_BATCH'");
        jdbc.update("DELETE FROM mail_template WHERE template_cd = 'ZZ_BATCH'");
        jdbc.update("DELETE FROM auth WHERE conn_id = 'MEMBER' AND menu_id IN (SELECT menu_id FROM menu WHERE link_url = '/adm/batch')");
        // SQL로 바꾼 주기를 스케줄러에도 다시 맞춘다(기동 때와 같은 경로)
        batchService.init();
    }

    @Test
    @DisplayName("작업 6종(CMS 5종 + 모집 하루 전 알림)이 주기·자동 실행·다음 실행 시각과 함께 보이고, 스케줄러에 등록돼 있다")
    void listShowsJobs() throws Exception {
        String body = list(admin());
        assertThat(JsonPath.<List<String>>read(body, "$.data.list[*].jobCd")).containsExactlyInAnyOrderElementsOf(JOBS);
        for (String job : JOBS) {
            Map<String, Object> row = row(body, job);
            assertThat((String) row.get("cronExpr")).isNotBlank();
            assertThat(row.get("autoYn")).isEqualTo("Y");
            assertThat(row.get("scheduledYn")).as(job + " 스케줄 등록").isEqualTo("Y");
            assertThat((String) row.get("nextRunDt")).as(job + " 다음 실행").isNotBlank();
        }
    }

    @Test
    @DisplayName("비로그인 401, 일반 회원 403 — 조회·변경·즉시 실행 모두")
    void guestAndMemberBlocked() throws Exception {
        String id = jobId("LOG_RETENTION");
        assertThat(post("/api/adm/batch/selectBatchList.do", "{}", null).statusCode()).isEqualTo(401);
        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/batch/selectBatchList.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/batch/updateBatch.do",
                "{\"rowId\":\"" + id + "\",\"cronExpr\":\"0 0 5 * * *\",\"autoYn\":\"Y\"}", user).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/batch/updateBatchRun.do", "{\"rowId\":\"" + id + "\"}", user).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("메뉴 권한을 줘도 관리자가 아니면 즉시 실행할 수 없다")
    void menuPermissionAloneIsNotEnough() throws Exception {
        // 회원 라이프사이클을 즉시 돌리면 휴면 전환·파기가 실제로 일어난다 — 메뉴를 잘못 열어 줘도 마지막 방어선이 있어야 한다
        jdbc.update("INSERT INTO auth (menu_id, conn_id, type, menu_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " SELECT menu_id, 'MEMBER', 'GRP', 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'"
                + " FROM menu WHERE link_url = '/adm/batch'");
        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/batch/updateBatchRun.do", "{\"rowId\":\"" + jobId("MEMBER_LIFECYCLE") + "\"}", user)
                .statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/batch/selectBatchList.do", "{}", user).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("주기를 바꾸면 저장되고 다음 실행 시각·시스템 상태에 바로 반영된다(재시작 불필요)")
    void changingCronReschedules() throws Exception {
        String admin = admin();
        assertThat(update(admin, "MAIL_LOG_PURGE", "0 15 2 * * *", "Y")).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT cron_expr FROM batch_job WHERE job_cd = 'MAIL_LOG_PURGE'", String.class))
                .isEqualTo("0 15 2 * * *");
        Map<String, Object> row = row(list(admin), "MAIL_LOG_PURGE");
        assertThat((String) row.get("nextRunDt")).endsWith("02:15:00");
        assertThat(row.get("scheduledYn")).isEqualTo("Y");

        String status = post("/api/adm/system/selectSystemView.do", "{}", admin).body();
        assertThat(JsonPath.<List<String>>read(status, "$.data.schedules[*].cron")).contains("0 15 2 * * *");
    }

    @Test
    @DisplayName("잘못된 cron·1분보다 잦은 주기는 저장되지 않는다")
    void invalidCronRejected() throws Exception {
        String admin = admin();
        assertThat(update(admin, "FILE_GC", "abc", "Y")).isEqualTo(400);
        assertThat(update(admin, "FILE_GC", "0 4 * * *", "Y")).as("5자리(초 없음)").isEqualTo(400);
        assertThat(update(admin, "FILE_GC", "* * * * * *", "Y")).as("매초").isEqualTo(400);
        assertThat(update(admin, "FILE_GC", "*/30 * * * * *", "Y")).as("30초마다").isEqualTo(400);
        assertThat(update(admin, "FILE_GC", "", "Y")).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT cron_expr FROM batch_job WHERE job_cd = 'FILE_GC'", String.class))
                .as("거절되면 값도 그대로").isEqualTo("0 0 4 * * *");
        assertThat(update(admin, "FILE_GC", "0 */5 * * * *", "Y")).as("5분마다는 허용").isEqualTo(200);
    }

    @Test
    @DisplayName("자동 실행을 끄면 스케줄러에서 빠지고, 다시 켜면 등록된다")
    void autoOffUnschedules() throws Exception {
        String admin = admin();
        assertThat(update(admin, "LOG_RETENTION", "0 30 3 * * *", "N")).isEqualTo(200);
        Map<String, Object> off = row(list(admin), "LOG_RETENTION");
        assertThat(off.get("autoYn")).isEqualTo("N");
        assertThat(off.get("scheduledYn")).isEqualTo("N");
        assertThat(off.get("nextRunDt")).isNull();

        assertThat(update(admin, "LOG_RETENTION", "0 30 3 * * *", "Y")).isEqualTo(200);
        assertThat(row(list(admin), "LOG_RETENTION").get("scheduledYn")).isEqualTo("Y");
    }

    @Test
    @DisplayName("즉시 실행은 바로 응답하고 뒤에서 돈다 — 이력이 성공으로 바뀌고 실제로 정리된다")
    void runNowExecutesInBackground() throws Exception {
        String admin = admin();
        // 실제 효과를 본다: 보존기간(90일) 지난 메일 이력을 하나 만들어 두고 메일 이력 정리를 즉시 실행
        post("/api/adm/mailtemplate/insertMailTemplate.do",
                "{\"templateCd\":\"ZZ_BATCH\",\"name\":\"배치\",\"subject\":\"제목\",\"content\":\"<p>본문</p>\"}", admin);
        post("/api/adm/maillog/insertMailLog.do", "{\"templateCd\":\"ZZ_BATCH\",\"toEmail\":\"zz-batch@example.com\"}", admin);
        jdbc.update("UPDATE mail_log SET reg_dt = NOW() - INTERVAL '400 days' WHERE template_cd = 'ZZ_BATCH'");

        assertThat(post("/api/adm/batch/updateBatchRun.do", "{\"rowId\":\"" + jobId("MAIL_LOG_PURGE") + "\"}", admin)
                .statusCode()).isEqualTo(200);

        Map<String, Object> log = awaitFinished("MAIL_LOG_PURGE");
        assertThat(log.get("status_cd")).isEqualTo("BATCH02");
        assertThat(log.get("manual_yn")).isEqualTo("Y");
        assertThat(log.get("reg_id")).as("누가 눌렀는지").isEqualTo("admin");
        assertThat(log.get("end_dt")).isNotNull();
        assertThat((String) log.get("result_msg")).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_log WHERE template_cd = 'ZZ_BATCH'", Integer.class))
                .as("작업이 실제로 돌았다").isZero();

        // 목록에도 마지막 실행 결과가 보인다
        Map<String, Object> row = row(list(admin), "MAIL_LOG_PURGE");
        assertThat(row.get("lastStatusCd")).isEqualTo("BATCH02");
        assertThat(row.get("lastManualYn")).isEqualTo("Y");
        // 이력 목록(variant=Log)에서도 보인다
        String logs = post("/api/adm/batch/selectBatchListLog.do", "{\"jobCd\":\"MAIL_LOG_PURGE\"}", admin).body();
        assertThat(JsonPath.<Integer>read(logs, "$.data.totalCount")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(logs, "$.data.list[*].statusName")).containsExactly("성공");
    }

    @Test
    @DisplayName("같은 작업이 실행 중이면 즉시 실행을 거절한다(겹쳐 돌지 않는다)")
    void runRejectedWhileRunning() throws Exception {
        jdbc.update("INSERT INTO batch_log (job_cd, manual_yn, status_cd, start_dt, reg_id, reg_ip)"
                + " VALUES ('LOG_RETENTION', 'N', 'BATCH01', NOW(), 'system', '127.0.0.1')");

        assertThat(post("/api/adm/batch/updateBatchRun.do", "{\"rowId\":\"" + jobId("LOG_RETENTION") + "\"}", admin())
                .statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_log WHERE job_cd = 'LOG_RETENTION'", Integer.class))
                .as("두 번째 실행 기록이 생기면 안 된다").isEqualTo(1);
        // 예약 실행도 겹치면 건너뛴다 — 기록만 안 남기고 작업은 도는 게 아니라 아예 돌지 않아야 한다
        jdbc.update("UPDATE config SET del_log_days = 365");
        jdbc.update("INSERT INTO event_log (event_cd, member_id, target_table, target_id, reg_dt, reg_ip)"
                + " VALUES ('INSERT', 'admin', 'zzbatch', 'old', NOW() - INTERVAL '400 days', '127.0.0.1')");
        batchService.runScheduled("LOG_RETENTION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_log WHERE job_cd = 'LOG_RETENTION'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_log WHERE target_table = 'zzbatch'", Integer.class))
                .as("겹친 회차는 정리를 하지 않는다").isEqualTo(1);
    }

    @Test
    @DisplayName("예약 실행도 이력이 남는다(실행자 system, 수동 아님)")
    void scheduledRunIsLogged() {
        batchService.runScheduled("LOG_RETENTION");

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT * FROM batch_log WHERE job_cd = 'LOG_RETENTION' ORDER BY batch_log_id DESC LIMIT 1");
        assertThat(log.get("status_cd")).isEqualTo("BATCH02");
        assertThat(log.get("manual_yn")).isEqualTo("N");
        assertThat(log.get("reg_id")).isEqualTo("system");
    }

    @Test
    @DisplayName("모집 하루 전 알림(이 저장소 전용)도 배치로 돌고 발송 건수가 남는다")
    void recruitRemindRunsAsBatch() {
        // 알림 내용·중복 방지는 RecruitRemindTest가 본다 — 여기서는 작업 코드가 그 배치에 이어져 있는지만
        batchService.runScheduled("RECRUIT_REMIND");

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT * FROM batch_log WHERE job_cd = 'RECRUIT_REMIND' ORDER BY batch_log_id DESC LIMIT 1");
        assertThat(log.get("status_cd")).isEqualTo("BATCH02");
        assertThat((String) log.get("result_msg")).contains("모임 알림");
    }

    @Test
    @DisplayName("실패한 실행은 실패로 남고 사유가 기록된다")
    void failedRunIsLogged() throws Exception {
        // 코드에 구현이 없는 작업 행 — 실행하면 예외가 난다(실패가 '실행 중'으로 남거나 삼켜지면 안 된다)
        jdbc.update("INSERT INTO batch_job (job_cd, name, cron_expr, auto_yn, sort_no, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES ('ZZ_UNKNOWN', '없는 작업', '0 0 1 * * *', 'Y', 99, 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')");

        assertThat(post("/api/adm/batch/updateBatchRun.do", "{\"rowId\":\"" + jobId("ZZ_UNKNOWN") + "\"}", admin())
                .statusCode()).isEqualTo(200);

        Map<String, Object> log = awaitFinished("ZZ_UNKNOWN");
        assertThat(log.get("status_cd")).isEqualTo("BATCH03");
        assertThat((String) log.get("result_msg")).contains("ZZ_UNKNOWN");
        assertThat(log.get("end_dt")).isNotNull();
    }

    @Test
    @DisplayName("서버가 실행 도중 내려가 '실행 중'으로 남은 이력은 기동 때 실패로 정리된다")
    void staleRunningRecoveredOnInit() {
        jdbc.update("INSERT INTO batch_log (job_cd, manual_yn, status_cd, start_dt, reg_id, reg_ip)"
                + " VALUES ('FILE_GC', 'N', 'BATCH01', NOW() - INTERVAL '1 hour', 'system', '127.0.0.1')");

        batchService.init();

        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM batch_log WHERE job_cd = 'FILE_GC'");
        assertThat(log.get("status_cd")).as("안 고치면 그 작업은 영원히 '실행 중'이라 다시 못 돌린다").isEqualTo("BATCH03");
        assertThat((String) log.get("result_msg")).contains("재시작");
        assertThat(log.get("end_dt")).isNotNull();
    }

    // ===== helpers =====

    private String admin() throws Exception {
        return accessToken("admin", "admin1234!");
    }

    private String list(String token) throws Exception {
        return post("/api/adm/batch/selectBatchList.do", "{}", token).body();
    }

    private Map<String, Object> row(String listBody, String jobCd) {
        List<Map<String, Object>> rows = JsonPath.read(listBody, "$.data.list[?(@.jobCd=='" + jobCd + "')]");
        assertThat(rows).as(jobCd + " 행").hasSize(1);
        return rows.get(0);
    }

    private String jobId(String jobCd) {
        return String.valueOf(jdbc.queryForObject("SELECT batch_job_id FROM batch_job WHERE job_cd = ?", Integer.class, jobCd));
    }

    private int update(String token, String jobCd, String cron, String autoYn) throws Exception {
        return post("/api/adm/batch/updateBatch.do",
                "{\"rowId\":\"" + jobId(jobCd) + "\",\"cronExpr\":\"" + cron + "\",\"autoYn\":\"" + autoYn + "\"}", token)
                .statusCode();
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
}
