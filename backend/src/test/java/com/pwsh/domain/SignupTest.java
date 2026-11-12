package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 셀프 회원가입·비밀번호 찾기 보강(CMS 1.2.35) — 회원가입 켜기·끄기(이 저장소 기본 켜짐) · 인증 코드 해시 · 응답 통일.
 *
 * <p>테스트 프로파일은 mail.enabled=false라 코드 메일이 실제로 나가지 않고, 발송이 실패하면 코드를 바로 만료시킨다.
 * 그래서 검증 경로는 코드를 해시로 직접 심어서 태운다(IntegrationTest.seedCode) — 저장 형식(해시)은 운영과 같다.
 * 필수 약관 미동의·동의 이력은 {@code PolicyAgreeTest}가 본다.
 *
 * <p>조용히 틀릴 수 있는 곳 — 꺼 둔 가입이 API로는 열려 있는 것, 인증 코드를 무한히 찍어 볼 수 있는 것, 없는 약관 번호가 동의 이력에
 * 남는 것, 그리고 비밀번호 찾기 응답이 계정 유무에 따라 달라져(재발송 제한·메일 실패) 아이디가 있는지 알려 주는 것.
 */
class SignupTest extends IntegrationTest {

    private static final String PW = "Test1234!@";

    private String optionalId;

    @BeforeEach
    void setUp() {
        jdbc.update("UPDATE config SET signup_yn = 'Y'");
        jdbc.update("INSERT INTO policy (title, content, type_cd, req_yn, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES ('ZZ마케팅', '<p>ZZ마케팅 본문</p>', 'POLICY03', 'N', 90, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')");
        optionalId = String.valueOf(jdbc.queryForObject("SELECT MAX(policy_id) FROM policy WHERE title = 'ZZ마케팅'", Integer.class));
        jdbc.update("DELETE FROM mail_log WHERE template_cd IN ('SIGNUP_CODE', 'RESET_CODE')");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("UPDATE config SET signup_yn = 'Y'"); // 이 저장소 기본값 — 끈 채로 두면 뒤에 도는 테스트의 가입이 403이 된다
        jdbc.update("DELETE FROM member_policy WHERE policy_id IN (SELECT policy_id FROM policy WHERE title LIKE 'ZZ%')");
        jdbc.update("DELETE FROM policy WHERE title LIKE 'ZZ%'");
        jdbc.update("DELETE FROM email_verification");
        jdbc.update("DELETE FROM mail_log WHERE template_cd IN ('SIGNUP_CODE', 'RESET_CODE')");
    }

    // ===== 켜기·끄기 =====

    @Test
    @DisplayName("기본은 켜져 있다 — 끄면 코드 발송·가입 모두 막히고, 로그인 화면은 그 사실을 안다")
    void signupOffBlocks() throws Exception {
        assertThat((String) JsonPath.read(post("/api/adm/config/selectConfigView.do", "{}", null).body(), "$.data.signupYn"))
                .as("가입이 늘 열린 커뮤니티라 기본 켜짐").isEqualTo("Y");

        jdbc.update("UPDATE config SET signup_yn = 'N'");
        assertThat(post("/api/auth/sendSignupCode", "{\"email\":\"zzsu@example.com\"}", null).statusCode()).isEqualTo(403);
        assertThat(signup("zzsu01", "zz가입01", "zzsu01@example.com").statusCode())
                .as("화면 링크만 숨기면 API로 가입된다").isEqualTo(403);
        assertThat(count("SELECT COUNT(*) FROM member WHERE member_id = 'zzsu01'")).isZero();
        assertThat((String) JsonPath.read(post("/api/adm/config/selectConfigView.do", "{}", null).body(), "$.data.signupYn"))
                .isEqualTo("N");
    }

    @Test
    @DisplayName("다시 켜려면 메일 발송이 켜져 있어야 한다(코드를 못 보내면 아무도 가입할 수 없다)")
    void enablingRequiresMail() throws Exception {
        jdbc.update("UPDATE config SET signup_yn = 'N'");
        String admin = accessToken("admin", "admin1234!");
        HttpResponse<String> r = saveConfig(admin, "Y");
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("mail.enabled");
        assertThat(jdbc.queryForObject("SELECT signup_yn FROM config", String.class)).isEqualTo("N");

        jdbc.update("UPDATE config SET signup_yn = 'Y'");
        assertThat(saveConfig(admin, "Y").statusCode()).as("이미 켜진 상태의 다른 설정 저장은 막지 않는다").isEqualTo(200);
        assertThat(saveConfig(admin, null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT signup_yn FROM config", String.class)).as("값을 안 보낸 저장은 그대로").isEqualTo("Y");
        assertThat(saveConfig(admin, "N").statusCode()).as("끄는 것은 메일과 상관없다").isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT signup_yn FROM config", String.class)).isEqualTo("N");
    }

    // ===== 가입 =====

    @Test
    @DisplayName("해시로 저장된 코드로 가입되고, 쓴 코드는 지운다 — 이메일은 앞뒤 공백·대소문자와 상관없이 같은 코드로 본다")
    void signupWithHashedCode() throws Exception {
        seedCode("zzsu02@example.com", "SIGNUP", "123456");
        HttpResponse<String> r = signupRaw("zzsu02", "zz가입02", " ZZsu02@Example.com ", "123456", requiredPlus(optionalId));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(count("SELECT COUNT(*) FROM email_verification")).as("쓴 코드는 지운다").isZero();
        assertThat(jdbc.queryForList("SELECT policy_id::text FROM member_policy WHERE member_id = 'zzsu02'", String.class))
                .as("선택 약관 동의도 남는다").contains(optionalId);
        assertThat((String) JsonPath.read(login("zzsu02", PW).body(), "$.data.accessToken")).isNotBlank();
    }

    @Test
    @DisplayName("없는 약관 번호는 동의 이력에 남지 않는다")
    void unknownPolicyIgnored() throws Exception {
        seedCode("zzsu03@example.com", "SIGNUP", "123456");
        assertThat(signupRaw("zzsu03", "zz가입03", "zzsu03@example.com", "123456", requiredPlus("999999")).statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT policy_id::text FROM member_policy WHERE member_id = 'zzsu03'", String.class))
                .doesNotContain("999999")
                .containsExactlyInAnyOrderElementsOf(jdbc.queryForList(
                        "SELECT policy_id::text FROM policy WHERE use_yn = 'Y' AND req_yn = 'Y'", String.class));
    }

    @Test
    @DisplayName("틀린 코드는 거절되고, 5번 틀리면 맞는 코드도 더 쓸 수 없다")
    void wrongCodeLimited() throws Exception {
        seedCode("zzsu04@example.com", "SIGNUP", "123456");
        assertThat(signupRaw("zzsu04", "zz가입04", "zzsu04@example.com", "654321", requiredPlus()).statusCode()).isEqualTo(400);
        assertThat(count("SELECT attempt_cnt FROM email_verification WHERE target = 'zzsu04@example.com'"))
                .as("가입이 실패로 되돌려져도 틀린 횟수는 남아야 한다").isEqualTo(1);
        for (int i = 0; i < 4; i++) {
            signupRaw("zzsu04", "zz가입04", "zzsu04@example.com", "654321", requiredPlus());
        }
        assertThat(signupRaw("zzsu04", "zz가입04", "zzsu04@example.com", "123456", requiredPlus()).statusCode())
                .as("6자리라 횟수 제한이 없으면 맞힐 때까지 시도할 수 있다").isEqualTo(400);
        assertThat(count("SELECT COUNT(*) FROM member WHERE member_id = 'zzsu04'")).isZero();
    }

    @Test
    @DisplayName("만료된 코드·다른 이메일의 코드는 거절된다")
    void expiredOrOtherEmailRejected() throws Exception {
        seedCode("zzsu05@example.com", "SIGNUP", "123456");
        jdbc.update("UPDATE email_verification SET expire_dt = NOW() - INTERVAL '1 minute'");
        assertThat(signupRaw("zzsu05", "zz가입05", "zzsu05@example.com", "123456", requiredPlus()).statusCode()).isEqualTo(400);

        seedCode("zzsu06@example.com", "SIGNUP", "123456");
        assertThat(signupRaw("zzsu07", "zz가입07", "zzsu07@example.com", "123456", requiredPlus()).statusCode()).isEqualTo(400);
        assertThat(count("SELECT COUNT(*) FROM member WHERE member_id IN ('zzsu05', 'zzsu07')")).isZero();
    }

    @Test
    @DisplayName("코드 발송: 메일을 못 보내면 실패를 알리고 코드를 남기지 않는다 — 재발송 제한은 그대로, 이메일 형식은 400")
    void sendSignupCodeWithoutMail() throws Exception {
        assertThat(post("/api/auth/sendSignupCode", "{\"email\":\"zzsu09@example.com\"}", null).statusCode()).isEqualTo(500);
        // 행은 재발송 제한(30초)의 근거라 남기되 바로 만료시킨다 — 받지 못한 코드를 유효한 것처럼 남기지 않는다
        assertThat(count("SELECT COUNT(*) FROM email_verification WHERE expire_dt > NOW()")).isZero();
        assertThat(count("SELECT COUNT(*) FROM email_verification WHERE code_hash LIKE '{bcrypt}%'")).as("코드는 해시로만").isEqualTo(1);
        assertThat(post("/api/auth/sendSignupCode", "{\"email\":\"ZZSU09@example.com\"}", null).statusCode())
                .as("메일이 실패해도 재발송 제한은 그대로(대소문자만 다른 주소도 같은 대상)").isEqualTo(400);
        assertThat(post("/api/auth/sendSignupCode", "{\"email\":\"not-an-email\"}", null).statusCode()).isEqualTo(400);
    }

    // ===== 비밀번호 찾기 =====

    @Test
    @DisplayName("비밀번호 찾기: 코드로 새 비밀번호를 정하면 기존 로그인은 끊기고 새 비밀번호로 들어온다 — 쓴 코드는 다시 못 쓴다")
    void resetPassword() throws Exception {
        assertThat(signup("zzsu10", "zz가입10", "zzsu10@example.com").statusCode()).isEqualTo(200);
        String oldToken = accessToken("zzsu10", PW);
        seedCode("zzsu10", "RESET", "123456");

        assertThat(reset("zzsu10", "000000", "New1234!@#").statusCode()).as("틀린 코드").isEqualTo(400);
        assertThat(reset("zzsu10", "123456", "New1234!@#").statusCode()).isEqualTo(200);
        assertThat(post("/api/auth/pwExtend", "{}", oldToken).statusCode()).as("기존 토큰은 무효").isEqualTo(401);
        assertThat(login("zzsu10", PW).statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(login("zzsu10", "New1234!@#").body(), "$.data.accessToken")).isNotBlank();
        assertThat(reset("zzsu10", "123456", "New5678!@#").statusCode()).as("쓴 코드는 다시 못 쓴다").isEqualTo(400);
    }

    @Test
    @DisplayName("비밀번호 찾기 코드 요청은 계정이 있든 없든·연달아 눌러도 같은 응답이다 — 메일은 있는 계정에만")
    void resetRequestDoesNotRevealAccount() throws Exception {
        assertThat(signup("zzsu12", "zz가입12", "zzsu12@example.com").statusCode()).isEqualTo(200);
        HttpResponse<String> exists = post("/api/auth/sendResetCode", "{\"memberId\":\"zzsu12\"}", null);
        HttpResponse<String> again = post("/api/auth/sendResetCode", "{\"memberId\":\"zzsu12\"}", null);
        HttpResponse<String> none = post("/api/auth/sendResetCode", "{\"memberId\":\"zzsu-none\"}", null);
        assertThat(exists.statusCode()).isEqualTo(200);
        assertThat(again.statusCode()).as("재발송 제한(30초)이 있는 계정에서만 400이면 아이디가 드러난다").isEqualTo(200);
        assertThat(none.statusCode()).isEqualTo(200);
        assertThat(exists.body()).isEqualTo(none.body()).isEqualTo(again.body());

        // 발송은 응답과 따로 뒤에서 한다(응답 시간 차이로도 드러나지 않게) — 있는 계정에만 이력이 생긴다
        awaitResetMails(1);
        Thread.sleep(500);
        assertThat(count("SELECT COUNT(*) FROM mail_log WHERE template_cd = 'RESET_CODE'"))
                .as("연달아 눌러도 재발송 제한으로 한 통, 없는 계정은 0통").isEqualTo(1);
    }

    // ===== 약관 공개 조회 =====

    @Test
    @DisplayName("약관 상세는 비로그인도 보지만 숫자가 아닌 번호는 400(500 아님)")
    void policyViewRejectsNonNumeric() throws Exception {
        assertThat(post("/api/adm/policy/selectPolicyView.do", "{\"rowId\":\"" + optionalId + "\"}", null).statusCode())
                .isEqualTo(200);
        assertThat(post("/api/adm/policy/selectPolicyView.do", "{\"rowId\":\"abc\"}", null).statusCode()).isEqualTo(400);
    }

    // ===== helpers =====

    /** 필수 약관 전부 + 추가로 넘길 번호 → JSON 배열 */
    private String requiredPlus(String... extra) {
        List<String> ids = new java.util.ArrayList<>(jdbc.queryForList(
                "SELECT policy_id::text FROM policy WHERE use_yn = 'Y' AND req_yn = 'Y'", String.class));
        ids.addAll(List.of(extra));
        return "[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]";
    }

    /** 코드를 직접 정해 가입 요청만 보낸다(IntegrationTest.signup은 맞는 코드를 심고 보낸다) */
    private HttpResponse<String> signupRaw(String memberId, String nickname, String email, String code, String policyIdsJson)
            throws Exception {
        return post("/api/auth/signup", "{\"memberId\":\"" + memberId + "\",\"password\":\"" + PW + "\",\"pwConfirm\":\"" + PW
                + "\",\"nickname\":\"" + nickname + "\",\"email\":\"" + email + "\",\"code\":\"" + code + "\","
                + "\"agreedPolicyIds\":" + policyIdsJson + "}", null);
    }

    private HttpResponse<String> reset(String memberId, String code, String newPw) throws Exception {
        return post("/api/auth/resetPassword", "{\"memberId\":\"" + memberId + "\",\"code\":\"" + code + "\",\"newPw\":\""
                + newPw + "\",\"pwConfirm\":\"" + newPw + "\"}", null);
    }

    private void awaitResetMails(int expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (count("SELECT COUNT(*) FROM mail_log WHERE template_cd = 'RESET_CODE'") >= expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("재설정 코드 메일 이력이 5초 안에 생기지 않았다");
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    /** 현재 설정 전체를 읽어 회원가입 허용만 바꿔 저장(null이면 그 필드를 보내지 않는다) */
    private HttpResponse<String> saveConfig(String admin, String signupYn) throws Exception {
        Map<String, Object> cfg = JsonPath.read(post("/api/adm/config/selectConfigView.do", "{}", admin).body(), "$.data");
        StringBuilder sb = new StringBuilder("{");
        if (signupYn != null) {
            sb.append("\"signupYn\":\"").append(signupYn).append("\",");
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
