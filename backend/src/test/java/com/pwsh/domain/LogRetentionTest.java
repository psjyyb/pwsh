package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.eventlog.service.EventLogService;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 로그 보관 기간 정리(환경설정 "로그 보관 일수").
 *
 * <p>조용히 틀릴 수 있는 곳 — 지우면 안 되는 기록(개인정보 접근로그·파기 이력)까지 같이 지우는 것,
 * 그리고 보관 일수를 너무 짧게 잡아 접속기록 보관 의무를 어기는 것. 둘 다 에러 없이 기록만 사라진다.
 */
class LogRetentionTest extends IntegrationTest {

    @Autowired
    private EventLogService eventLogService;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM event_log WHERE target_table = 'zzret'");
        jdbc.update("DELETE FROM login_log WHERE login_id LIKE 'zzret%'");
        jdbc.update("DELETE FROM privacy_log WHERE request_uri = '/zzret'");
        jdbc.update("DELETE FROM destroy_log WHERE member_id LIKE 'zzret%'");
        jdbc.update("UPDATE config SET del_log_days = 365");
    }

    @Test
    @DisplayName("보관 일수가 지난 활동로그·로그인 이력만 지우고 그 안쪽은 남긴다")
    void purgesOnlyExpiredRows() {
        jdbc.update("UPDATE config SET del_log_days = 365");
        eventRow("old", 400);
        eventRow("new", 10);
        loginRow("zzret-old", 400);
        loginRow("zzret-new", 10);

        eventLogService.purgeExpiredLogs();

        assertThat(eventIds()).containsExactly("new");
        assertThat(loginIds()).containsExactly("zzret-new");
    }

    @Test
    @DisplayName("0이면 아무것도 지우지 않는다")
    void zeroMeansKeepForever() {
        jdbc.update("UPDATE config SET del_log_days = 0");
        eventRow("old", 4000);
        loginRow("zzret-old", 4000);

        eventLogService.purgeExpiredLogs();

        assertThat(eventIds()).containsExactly("old");
        assertThat(loginIds()).containsExactly("zzret-old");
    }

    @Test
    @DisplayName("DB에 하한(365일)보다 짧게 들어가 있어도 하한으로 지킨다")
    void tooShortValueIsRaisedToFloor() {
        // 화면 검증을 거치지 않은 값(직접 SQL·옛 데이터) — 그대로 쓰면 접속기록이 너무 일찍 사라진다
        jdbc.update("UPDATE config SET del_log_days = 30");
        eventRow("mid", 100);
        eventRow("old", 400);

        eventLogService.purgeExpiredLogs();

        assertThat(eventIds()).containsExactly("mid");
    }

    @Test
    @DisplayName("개인정보 접근로그·파기 이력은 아무리 오래돼도 지우지 않는다")
    void legalRecordsAreNeverPurged() {
        jdbc.update("UPDATE config SET del_log_days = 365");
        jdbc.update("INSERT INTO privacy_log (member_id, request_uri, sql_ids, target_ids, access_cnt, reg_dt, reg_ip)"
                + " VALUES ('admin', '/zzret', 'zz', 'zz', 1, NOW() - INTERVAL '2000 days', '127.0.0.1')");
        jdbc.update("INSERT INTO destroy_log (member_id, destroy_cd, reg_dt)"
                + " VALUES ('zzret-m', 'DESTROY01', NOW() - INTERVAL '2000 days')");

        eventLogService.purgeExpiredLogs();

        // 접근기록은 법정 보존기간이, 파기 이력은 증빙 의무가 있다 — 이 정리 대상이 아니다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM privacy_log WHERE request_uri = '/zzret'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM destroy_log WHERE member_id = 'zzret-m'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("환경설정에서 하한보다 짧은 보관 일수는 저장되지 않는다(0과 하한 이상은 된다)")
    void configRejectsTooShortRetention() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        assertThat(saveRetention(admin, "30")).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT del_log_days FROM config", Integer.class))
                .as("거절되면 값도 그대로여야 한다").isEqualTo(365);
        assertThat(saveRetention(admin, "-1")).isEqualTo(400);
        assertThat(saveRetention(admin, "0")).isEqualTo(200);
        assertThat(saveRetention(admin, "730")).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT del_log_days FROM config", Integer.class)).isEqualTo(730);
    }

    @Test
    @DisplayName("시스템 상태 화면에 로그 정리 배치가 보인다")
    void batchIsListedInSystemStatus() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String body = post("/api/adm/system/selectSystemView.do", "{}", admin).body();
        assertThat(body).contains("로그 보관 정리");
    }

    // ===== helpers =====

    private void eventRow(String targetId, int daysAgo) {
        jdbc.update("INSERT INTO event_log (event_cd, member_id, target_table, target_id, reg_dt, reg_ip)"
                + " VALUES ('INSERT', 'admin', 'zzret', ?, NOW() - (? || ' days')::INTERVAL, '127.0.0.1')",
                targetId, String.valueOf(daysAgo));
    }

    private void loginRow(String loginId, int daysAgo) {
        jdbc.update("INSERT INTO login_log (login_id, result_cd, reg_dt, reg_ip)"
                + " VALUES (?, 'LOGIN01', NOW() - (? || ' days')::INTERVAL, '127.0.0.1')",
                loginId, String.valueOf(daysAgo));
    }

    private java.util.List<String> eventIds() {
        return jdbc.queryForList("SELECT target_id FROM event_log WHERE target_table = 'zzret' ORDER BY target_id",
                String.class);
    }

    private java.util.List<String> loginIds() {
        return jdbc.queryForList("SELECT login_id FROM login_log WHERE login_id LIKE 'zzret%' ORDER BY login_id",
                String.class);
    }

    /** 현재 설정 전체를 읽어 보관 일수만 바꿔 저장 — 일부만 보내면 다른 설정이 지워진다(AccessIpTest와 같은 이유). */
    private int saveRetention(String admin, String days) throws Exception {
        String view = post("/api/adm/config/selectConfigView.do", "{}", admin).body();
        Map<String, Object> cfg = JsonPath.read(view, "$.data");
        StringBuilder sb = new StringBuilder("{\"delLogDays\":\"").append(days).append('"');
        for (String k : new String[] {"failCntLimit", "failLockMins", "passwordExpireDays", "sessionExpireMins",
                "title", "accIpYn", "maintYn", "maintMessage", "dormantDays", "dormantNotifyDays", "destroyDays"}) {
            Object v = cfg.get(k);
            sb.append(",\"").append(k).append("\":")
                    .append(v == null ? "null" : "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return post("/api/adm/config/updateConfig.do", sb.append('}').toString(), admin).statusCode();
    }
}
