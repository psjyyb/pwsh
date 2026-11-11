package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 2차 인증(이메일 코드) — 환경설정 "관리자 2차 인증"을 켜면 관리자는 비밀번호 다음에 메일로 받은 6자리 코드를 넣어야 토큰을 받는다.
 *
 * <p>테스트 프로파일은 mail.enabled=false라 실제로 보내지 않지만 발송 이력(mail_log)에 본문이 남는다 — 코드는 거기서 읽는다.
 * 같은 이유로 켜기 검증(메일 꺼짐이면 거절)을 화면 경로로는 통과할 수 없어, 켜진 상태는 SQL로 만든다.
 *
 * <p>조용히 틀릴 수 있는 곳 — 비밀번호 단계에서 토큰이 새는 것, 틀린 코드를 무한히 시도할 수 있는 것, 한 번 쓴 코드·만료된 코드가
 * 다시 통하는 것, 그리고 켜는 순간 메일을 못 받는 관리자가 잠기는 것.
 */
class AdminOtpTest extends IntegrationTest {

    private static final String PW = "Test1234!@";
    /** 템플릿은 코드를 단독 칸에 넣는다 — 본문의 다른 숫자(시각·IP)와 섞이지 않게 */
    private static final Pattern CODE = Pattern.compile(">\\s*(\\d{6})\\s*<");

    private Integer failLimit;

    @BeforeEach
    void remember() {
        failLimit = jdbc.queryForObject("SELECT fail_cnt_limit FROM config", Integer.class);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("UPDATE config SET admin_otp_yn = 'N', fail_cnt_limit = ?", failLimit);
        jdbc.update("DELETE FROM login_otp");
        jdbc.update("DELETE FROM login_log WHERE login_id LIKE 'zzotp%' OR login_id = ''");
        jdbc.update("DELETE FROM mail_log WHERE template_cd = 'ADMIN_LOGIN_CODE'");
        jdbc.update("DELETE FROM auth_member WHERE member_id LIKE 'zzotp%'");
        jdbc.update("DELETE FROM member WHERE member_id LIKE 'zzotp%'");
    }

    @Test
    @DisplayName("켜져 있으면 관리자는 비밀번호만으로 토큰을 받지 못하고, 코드가 그 관리자 메일로 나간다")
    void adminGetsChallengeInsteadOfToken() throws Exception {
        String admin = adminToken();
        createAdmin(admin, "zzotp01", "zzotp01@example.com");
        otpOn();

        HttpResponse<String> res = login("zzotp01", PW);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(res.body(), "$.data.otpKey")).isNotBlank();
        assertThat(res.body()).doesNotContain("accessToken").doesNotContain("refreshToken");
        assertThat(lastResult("zzotp01")).as("코드 발송 단계로 남는다(성공 아님)").isEqualTo("LOGIN10");

        String logs = post("/api/adm/maillog/selectMailLogList.do",
                "{\"filterKeyword\":\"zzotp01@example.com\",\"pageNo\":1,\"pageSize\":10}", admin).body();
        assertThat(JsonPath.<List<String>>read(logs, "$.data.list[*].templateCd")).contains("ADMIN_LOGIN_CODE");
        String code = lastCode();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_otp WHERE code_hash = ?", Integer.class, code))
                .as("코드는 해시로만 저장한다").isZero();
    }

    @Test
    @DisplayName("맞는 코드를 넣으면 토큰이 나오고 관리 화면을 쓸 수 있다 — 한 번 쓴 코드는 다시 안 된다")
    void correctCodeCompletesLogin() throws Exception {
        createAdmin(adminToken(), "zzotp01", "zzotp01@example.com");
        otpOn();
        String key = challenge("zzotp01");
        String code = lastCode();

        HttpResponse<String> ok = otp(key, code);
        assertThat(ok.statusCode()).isEqualTo(200);
        String token = JsonPath.read(ok.body(), "$.data.accessToken");
        assertThat(post("/api/adm/member/selectMemberList.do", "{}", token).statusCode()).isEqualTo(200);
        assertThat(lastResult("zzotp01")).isEqualTo("LOGIN01");

        assertThat(otp(key, code).statusCode()).as("같은 키·코드 재사용").isEqualTo(401);
    }

    @Test
    @DisplayName("틀린 코드는 거절되고 실패로 남으며 로그인 실패 횟수에 더해진다 — 한 번 틀려도 같은 키로 다시 시도할 수 있다")
    void wrongCodeRejected() throws Exception {
        createAdmin(adminToken(), "zzotp01", "zzotp01@example.com");
        otpOn();
        String key = challenge("zzotp01");
        String code = lastCode();

        assertThat(otp(key, wrong(code)).statusCode()).isEqualTo(401);
        assertThat(lastResult("zzotp01")).isEqualTo("LOGIN08");
        assertThat(jdbc.queryForObject("SELECT fail_cnt FROM member WHERE member_id = 'zzotp01'", Integer.class))
                .isEqualTo(1);

        assertThat(otp(key, code).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("한 키로 5번 틀리면 그 키는 버려진다(맞는 코드도 안 된다)")
    void fiveWrongCodesKillChallenge() throws Exception {
        jdbc.update("UPDATE config SET fail_cnt_limit = 10"); // 계정 잠금과 구분하려고 잠금 기준을 넉넉히
        createAdmin(adminToken(), "zzotp01", "zzotp01@example.com");
        otpOn();
        String key = challenge("zzotp01");
        String code = lastCode();

        for (int i = 0; i < 5; i++) {
            assertThat(otp(key, wrong(code)).statusCode()).isEqualTo(401);
        }
        assertThat(jdbc.queryForObject("SELECT use_yn FROM login_otp WHERE otp_key = ?", String.class, key))
                .isEqualTo("N");
        assertThat(otp(key, code).statusCode()).as("6자리라 횟수 제한이 없으면 맞힐 때까지 시도할 수 있다").isEqualTo(401);
        assertThat(statusCd("zzotp01")).as("잠금 기준(10) 아래라 계정은 정상").isEqualTo("STATUS01");
    }

    @Test
    @DisplayName("코드를 계속 틀리면 비밀번호와 같은 기준으로 계정이 잠긴다")
    void wrongCodesLockAccount() throws Exception {
        jdbc.update("UPDATE config SET fail_cnt_limit = 3");
        createAdmin(adminToken(), "zzotp01", "zzotp01@example.com");
        otpOn();
        String key = challenge("zzotp01");
        String code = lastCode();

        for (int i = 0; i < 3; i++) {
            otp(key, wrong(code));
        }
        assertThat(statusCd("zzotp01")).isEqualTo("STATUS02");
        assertThat(otp(key, code).statusCode()).as("잠긴 계정은 맞는 코드로도 못 들어온다").isEqualTo(401);
    }

    @Test
    @DisplayName("만료된 코드는 거절된다")
    void expiredChallengeRejected() throws Exception {
        createAdmin(adminToken(), "zzotp01", "zzotp01@example.com");
        otpOn();
        String key = challenge("zzotp01");
        jdbc.update("UPDATE login_otp SET expire_dt = NOW() - INTERVAL '1 minute' WHERE otp_key = ?", key);

        assertThat(otp(key, lastCode()).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("다시 로그인하면 앞서 받은 코드는 쓸 수 없다(마지막 메일의 코드만 유효)")
    void newLoginReplacesOldChallenge() throws Exception {
        createAdmin(adminToken(), "zzotp01", "zzotp01@example.com");
        otpOn();
        String key1 = challenge("zzotp01");
        String code1 = lastCode();
        String key2 = challenge("zzotp01");

        assertThat(otp(key1, code1).statusCode()).isEqualTo(401);
        assertThat(otp(key2, lastCode()).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("없는 키·빈 값은 거절된다")
    void garbageRejected() throws Exception {
        assertThat(otp("no-such-key", "123456").statusCode()).isEqualTo(401);
        assertThat(otp("", "").statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("일반 회원은 켜져 있어도 비밀번호만으로 로그인한다")
    void memberNotAffected() throws Exception {
        createMember(adminToken(), "zzotp05");
        otpOn();

        HttpResponse<String> res = login("zzotp05", PW);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(res.body(), "$.data.accessToken")).isNotBlank();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 코드 메일을 보내지 않는다")
    void wrongPasswordSendsNoCode() throws Exception {
        createAdmin(adminToken(), "zzotp01", "zzotp01@example.com");
        otpOn();

        assertThat(login("zzotp01", "Wrong1234!@").statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_log WHERE template_cd = 'ADMIN_LOGIN_CODE'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("이메일이 없는 관리자는 코드를 받을 수 없어 로그인이 막히고, 이유가 안내된다")
    void adminWithoutEmailBlocked() throws Exception {
        createAdmin(adminToken(), "zzotp02", null);
        otpOn();

        HttpResponse<String> res = login("zzotp02", PW);
        assertThat(res.statusCode()).isEqualTo(403);
        assertThat(res.body()).contains("이메일").doesNotContain("accessToken");
        assertThat(lastResult("zzotp02")).isEqualTo("LOGIN08");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_otp", Integer.class)).isZero();
    }

    @Test
    @DisplayName("꺼져 있으면 관리자도 바로 토큰을 받는다")
    void offMeansDirectToken() throws Exception {
        createAdmin(adminToken(), "zzotp01", "zzotp01@example.com");

        HttpResponse<String> res = login("zzotp01", PW);
        assertThat((String) JsonPath.read(res.body(), "$.data.accessToken")).isNotBlank();
        assertThat(res.body()).doesNotContain("otpKey");
    }

    @Test
    @DisplayName("켜려면 메일 발송이 켜져 있고 모든 관리자에게 이메일이 있어야 한다 — 아니면 이유와 명단을 알려 주고 거절")
    void enablingRequiresMailAndEmails() throws Exception {
        String admin = adminToken();
        createAdmin(admin, "zzotp02", null);
        createAdmin(admin, "zzotp03", ""); // 빈 문자열도 암호화돼 NULL이 아니게 저장된다 — 이것도 이메일 없음이다
        createAdmin(admin, "zzotp04", "zzotp04@example.com");

        HttpResponse<String> r = saveConfig(admin, "Y");
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("mail.enabled").contains("zzotp02").contains("zzotp03")
                .as("이메일 있는 관리자는 명단에 없다").doesNotContain("zzotp04");
        assertThat(otpYn()).as("거절되면 그대로 꺼져 있다").isEqualTo("N");
        assertThat(saveConfig(admin, "N").statusCode()).isEqualTo(200);

        // 이미 켜져 있을 때(직접 SQL 등)는 다른 설정 저장을 막지 않는다 — 막으면 사이트명 하나 못 바꾼다
        otpOn();
        assertThat(saveConfig(admin, "Y").statusCode()).isEqualTo(200);
        assertThat(saveConfig(admin, null).statusCode()).isEqualTo(200);
        assertThat(otpYn()).as("값을 안 보낸 저장은 기존 값을 지운다면 2차 인증이 몰래 꺼진다").isEqualTo("Y");
        assertThat((String) JsonPath.read(post("/api/adm/config/selectConfigView.do", "{}", admin).body(),
                "$.data.adminOtpYn")).isEqualTo("Y");
    }

    // ===== helpers =====

    private String adminToken() throws Exception {
        return accessToken("admin", "admin1234!");
    }

    private void otpOn() {
        jdbc.update("UPDATE config SET admin_otp_yn = 'Y'");
    }

    private String otpYn() {
        return jdbc.queryForObject("SELECT admin_otp_yn FROM config", String.class);
    }

    private void createAdmin(String adminToken, String memberId, String email) throws Exception {
        assertThat(post("/api/adm/member/insertMember.do",
                "{\"memberId\":\"" + memberId + "\",\"memberName\":\"테스트관리자\",\"typeCd\":\"MEM02\","
                        + "\"statusCd\":\"STATUS01\",\"password\":\"" + PW + "\""
                        + (email == null ? "" : ",\"email\":\"" + email + "\"") + "}", adminToken).statusCode())
                .isEqualTo(200);
        assertThat(post("/api/adm/member/updateMemberAuthGroup.do",
                "{\"memberId\":\"" + memberId + "\",\"authGroupIds\":[\"ADMIN\"]}", adminToken).statusCode())
                .isEqualTo(200);
    }

    /** 비밀번호 단계 → 받은 확인 키 */
    private String challenge(String memberId) throws Exception {
        HttpResponse<String> res = login(memberId, PW);
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return JsonPath.read(res.body(), "$.data.otpKey");
    }

    private HttpResponse<String> otp(String key, String code) throws Exception {
        return post("/api/auth/loginOtp", "{\"otpKey\":\"" + key + "\",\"code\":\"" + code + "\"}", null);
    }

    /** 가장 최근 코드 메일 본문에서 6자리 코드 */
    private String lastCode() {
        String content = jdbc.queryForObject("SELECT content FROM mail_log WHERE template_cd = 'ADMIN_LOGIN_CODE'"
                + " ORDER BY mail_log_id DESC LIMIT 1", String.class);
        Matcher m = CODE.matcher(content);
        assertThat(m.find()).as("본문에 코드가 있어야 한다").isTrue();
        return m.group(1);
    }

    private static String wrong(String code) {
        char last = code.charAt(5);
        return code.substring(0, 5) + (char) ('0' + (last - '0' + 1) % 10);
    }

    private String lastResult(String loginId) {
        return jdbc.queryForObject("SELECT result_cd FROM login_log WHERE login_id = ? ORDER BY login_log_id DESC LIMIT 1",
                String.class, loginId);
    }

    private String statusCd(String memberId) {
        return jdbc.queryForObject("SELECT status_cd FROM member WHERE member_id = ?", String.class, memberId);
    }

    /** 현재 설정 전체를 읽어 2차 인증만 바꿔 저장(null이면 그 필드를 보내지 않는다) — 일부만 보내면 다른 설정이 지워진다 */
    private HttpResponse<String> saveConfig(String admin, String otpYn) throws Exception {
        Map<String, Object> cfg = JsonPath.read(post("/api/adm/config/selectConfigView.do", "{}", admin).body(), "$.data");
        StringBuilder sb = new StringBuilder("{");
        if (otpYn != null) {
            sb.append("\"adminOtpYn\":\"").append(otpYn).append("\",");
        }
        boolean first = true;
        for (String k : new String[] {"failCntLimit", "failLockMins", "passwordExpireDays", "sessionExpireMins", "delLogDays",
                "title", "accIpYn", "maintYn", "maintMessage", "dormantDays", "dormantNotifyDays", "destroyDays"}) {
            Object v = cfg.get(k);
            sb.append(first ? "" : ",").append('"').append(k).append("\":")
                    .append(v == null ? "null" : "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
            first = false;
        }
        return post("/api/adm/config/updateConfig.do", sb.append('}').toString(), admin);
    }
}
