package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.accessip.service.AccessIpService;
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
 * 설치 점검 — 운영에 올리기 전에 빠뜨리기 쉬운 설정(기본 키·기본 비밀번호·개발 프로파일·업로드 경로·메일·배치)을 한 장으로 본다.
 *
 * <p>조용히 틀릴 수 있는 곳 — 점검 결과에 <b>비밀값 자체가 실려 나가는 것</b>(키·비밀번호를 "확인용"으로 보여 주면 그 화면이 유출 통로다),
 * 위험한 상태를 OK로 보이는 것, 그리고 관리자가 아닌 사람이 이 목록을 보는 것(어떤 방어가 꺼져 있는지 알려 주는 지도다).
 */
class InstallCheckTest extends IntegrationTest {

    @Autowired
    private AccessIpService accessIpService;

    @Autowired
    private BatchService batchService;

    @AfterEach
    void cleanup() {
        // 회원가입은 이 저장소 기본이 켜짐(V10) — 끈 채로 두면 뒤에 도는 테스트의 가입이 403이 된다
        jdbc.update("UPDATE config SET admin_otp_yn = 'N', acc_ip_yn = 'N', maint_yn = 'N', signup_yn = 'Y'");
        accessIpService.evict();
        jdbc.update("DELETE FROM flyway_schema_history WHERE version = '9999'");
        jdbc.update("UPDATE batch_job SET auto_yn = 'Y'");
        jdbc.update("DELETE FROM batch_log");
        batchService.init();
    }

    @Test
    @DisplayName("관리자만 본다 — 비로그인 401, 회원 403, 메뉴 권한을 줘도 관리자가 아니면 403")
    void adminOnly() throws Exception {
        assertThat(post("/api/adm/system/selectSystemListCheck.do", "{}", null).statusCode()).isEqualTo(401);
        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/system/selectSystemListCheck.do", "{}", user).statusCode()).isEqualTo(403);

        Integer menuId = jdbc.queryForObject("SELECT menu_id FROM menu WHERE area = 'ADM' AND link_url = '/adm/system'", Integer.class);
        jdbc.update("INSERT INTO auth (menu_id, conn_id, type, menu_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (?, 'MEMBER', 'GRP', 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", menuId);
        try {
            assertThat(post("/api/adm/system/selectSystemListCheck.do", "{}", accessToken("user", "user1234!")).statusCode())
                    .as("어떤 방어가 꺼져 있는지 알려 주는 목록이라 메뉴가 열려도 관리자만").isEqualTo(403);
        } finally {
            jdbc.update("DELETE FROM auth WHERE menu_id = ? AND conn_id = 'MEMBER'", menuId);
        }
    }

    @Test
    @DisplayName("점검 항목이 모두 있고 요약 건수가 항목과 맞는다")
    void allChecksPresent() throws Exception {
        String body = check();
        List<String> keys = JsonPath.read(body, "$.data.list[*].key");
        assertThat(keys).contains("CRYPTO_KEY", "JWT_SECRET", "ADMIN_DEFAULT_PW", "SAMPLE_ACCOUNT", "ADMIN_OTP", "ACCESS_IP",
                "PROFILE", "HSTS", "CSP", "CORS", "LOGIN_URL", "MIGRATION", "UPLOAD_DIR", "MAIL", "BATCH", "MAINT", "SIGNUP");
        List<String> levels = JsonPath.read(body, "$.data.list[*].level");
        assertThat(levels).allMatch(l -> List.of("OK", "WARN", "FAIL").contains(l));
        assertThat(JsonPath.<Integer>read(body, "$.data.summary.FAIL"))
                .isEqualTo((int) levels.stream().filter("FAIL"::equals).count());
        assertThat(JsonPath.<Integer>read(body, "$.data.summary.WARN"))
                .isEqualTo((int) levels.stream().filter("WARN"::equals).count());
        // 안내가 비면 "그래서 어떻게 하라는 건지" 알 수 없다
        assertThat(JsonPath.<List<String>>read(body, "$.data.list[?(@.level != 'OK')].fix")).doesNotContainNull()
                .allMatch(s -> !s.isBlank());
    }

    @Test
    @DisplayName("비밀값(암호화 키·JWT 비밀키·기본 비밀번호)은 결과에 실리지 않는다")
    void noSecretsInResponse() throws Exception {
        String body = check();
        assertThat(body).doesNotContain("psjyyb")                       // 개인정보 암호화 키 기본값(BaseVO)
                .doesNotContain("test-secret-framework")                 // 테스트 JWT 비밀키
                .doesNotContain("admin1234!").doesNotContain("user1234!");
    }

    @Test
    @DisplayName("기본 키·기본 비밀번호·샘플 계정·개발 프로파일은 위험으로 보인다")
    void defaultsAreFlagged() throws Exception {
        String body = check();
        // 테스트 실행 환경엔 APP_CRYPTO_KEY가 없다(있으면 OK) — 없으면 개인정보가 저장소에 적힌 기본 키로 암호화된다
        String cryptoExpected = System.getenv("APP_CRYPTO_KEY") == null || System.getenv("APP_CRYPTO_KEY").isBlank() ? "FAIL" : "OK";
        assertThat(level(body, "CRYPTO_KEY")).isEqualTo(cryptoExpected);
        assertThat(level(body, "ADMIN_DEFAULT_PW")).as("테스트의 admin은 기본 비밀번호 그대로다").isEqualTo("FAIL");
        assertThat(level(body, "SAMPLE_ACCOUNT")).isEqualTo("WARN");
        assertThat(level(body, "PROFILE")).as("test 프로파일").isEqualTo("WARN");
        assertThat(level(body, "JWT_SECRET")).as("테스트 비밀키는 32자 — 기본값도 짧지도 않다").isEqualTo("OK");
        assertThat(level(body, "MAIL")).as("테스트는 메일 발송 꺼짐").isEqualTo("WARN");
        assertThat(level(body, "CORS")).as("기본 허용 오리진 localhost").isEqualTo("WARN");
        assertThat(level(body, "HSTS")).isEqualTo("WARN");
        assertThat(level(body, "MIGRATION")).isEqualTo("OK");
        assertThat(level(body, "UPLOAD_DIR")).as("쓰기는 되지만 경로를 환경변수로 안 줬다").isIn("OK", "WARN");
    }

    @Test
    @DisplayName("설정에 따라 결과가 바뀐다 — 2차 인증·접속 IP 제한·점검 모드")
    void reflectsConfig() throws Exception {
        // 2차 인증을 켜면 admin 재로그인은 코드 단계가 되므로 토큰을 먼저 받아 둔다
        String admin = accessToken("admin", "admin1234!");
        assertThat(level(check(admin), "ADMIN_OTP")).isEqualTo("WARN");
        jdbc.update("UPDATE config SET admin_otp_yn = 'Y'");
        assertThat(level(check(admin), "ADMIN_OTP")).isEqualTo("OK");

        jdbc.update("UPDATE config SET acc_ip_yn = 'N'");
        accessIpService.evict();
        assertThat(message(check(admin), "ACCESS_IP")).contains("꺼져");
        // 켜 두었지만 허용 IP가 없으면 실제로는 아무것도 막지 않는다 — 켜져 있다고 OK로 보이면 안 된다
        jdbc.update("DELETE FROM access_ip");
        jdbc.update("UPDATE config SET acc_ip_yn = 'Y'");
        accessIpService.evict();
        String body = check(admin);
        assertThat(level(body, "ACCESS_IP")).isEqualTo("WARN");
        assertThat(message(body, "ACCESS_IP")).contains("허용 IP");

        assertThat(level(check(admin), "MAINT")).isEqualTo("OK");
        jdbc.update("UPDATE config SET maint_yn = 'Y'");
        assertThat(level(check(admin), "MAINT")).isEqualTo("WARN");

        // 회원가입: 켜져 있는데(이 저장소 기본) 메일이 꺼져 있으면(테스트 환경) 아무도 가입할 수 없다 / 끄면 OK
        assertThat(level(check(admin), "SIGNUP")).isEqualTo("FAIL");
        jdbc.update("UPDATE config SET signup_yn = 'N'");
        assertThat(level(check(admin), "SIGNUP")).isEqualTo("OK");
    }

    @Test
    @DisplayName("실패한 마이그레이션이 있으면 실패로 보인다")
    void failedMigrationIsFlagged() throws Exception {
        jdbc.update("INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum,"
                + " installed_by, installed_on, execution_time, success)"
                + " VALUES (9999, '9999', 'zz fail', 'SQL', 'V9999__zz.sql', 0, 'test', NOW(), 0, false)");
        String body = check();
        assertThat(level(body, "MIGRATION")).isEqualTo("FAIL");
        assertThat(message(body, "MIGRATION")).contains("9999");
    }

    @Test
    @DisplayName("자동 실행이 꺼진 배치·마지막 실행이 실패한 배치를 알려 준다")
    void batchProblemsAreFlagged() throws Exception {
        assertThat(level(check(), "BATCH")).isEqualTo("OK");

        jdbc.update("UPDATE batch_job SET auto_yn = 'N' WHERE job_cd = 'FILE_GC'");
        jdbc.update("INSERT INTO batch_log (job_cd, manual_yn, status_cd, result_msg, start_dt, end_dt, reg_id, reg_ip)"
                + " VALUES ('LOG_RETENTION', 'N', 'BATCH03', 'zz 실패', NOW(), NOW(), 'system', '127.0.0.1')");
        String body = check();
        assertThat(level(body, "BATCH")).isEqualTo("WARN");
        assertThat(message(body, "BATCH")).contains("고아 파일 정리").contains("로그 보관 정리");
    }

    // ===== helpers =====

    private String check() throws Exception {
        return check(accessToken("admin", "admin1234!"));
    }

    private String check(String adminToken) throws Exception {
        return post("/api/adm/system/selectSystemListCheck.do", "{}", adminToken).body();
    }

    private Map<String, Object> item(String body, String key) {
        List<Map<String, Object>> rows = JsonPath.read(body, "$.data.list[?(@.key=='" + key + "')]");
        assertThat(rows).as(key).hasSize(1);
        return rows.get(0);
    }

    private String level(String body, String key) {
        return (String) item(body, key).get("level");
    }

    private String message(String body, String key) {
        return (String) item(body, key).get("message");
    }
}
