package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.mail.service.MailService;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 메일 — 템플릿 · 치환 · 발송 이력 검증.
 *
 * <p>테스트 프로파일은 {@code mail.enabled=false}(기본값)라 실제 SMTP로 나가지 않는다.
 * 그래도 <b>치환 결과와 이력 적재는 그대로 일어나므로</b> 여기서 검증하는 것이 실제 동작과 같다.
 * SMTP 전송 자체는 서버가 없어 자동 검증 대상이 아니다(운영에서 mail.enabled=true로 확인).
 */
class MailTest extends IntegrationTest {

    @Autowired
    private MailService mailService;

    @Test
    @DisplayName("템플릿 등록·수정·삭제가 목록에 반영된다")
    void templateCrud() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = insertTemplate(admin, "ZZ_CRUD", "테스트", "제목 {{a}}", "<p>{{a}}</p>");

        String view = post("/api/adm/mailtemplate/selectMailTemplateView.do",
                "{\"rowId\":\"" + id + "\"}", admin).body();
        assertThat(JsonPath.<String>read(view, "$.data.subject")).isEqualTo("제목 {{a}}");

        assertThat(post("/api/adm/mailtemplate/updateMailTemplate.do",
                "{\"rowId\":\"" + id + "\",\"templateCd\":\"ZZ_CRUD\",\"name\":\"수정\","
                        + "\"subject\":\"바뀐제목\",\"content\":\"<p>x</p>\"}", admin).statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(post("/api/adm/mailtemplate/selectMailTemplateView.do",
                "{\"rowId\":\"" + id + "\"}", admin).body(), "$.data.name")).isEqualTo("수정");

        deleteTemplate(admin, id);
        assertThat(templateCodes(admin)).doesNotContain("ZZ_CRUD");
    }

    @Test
    @DisplayName("사용중인 템플릿 코드는 중복 등록되지 않는다 — 중복이면 어느 쪽이 나갈지 알 수 없다")
    void duplicateCodeRejected() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = insertTemplate(admin, "ZZ_DUP", "원본", "제목", "<p>본문</p>");

        assertThat(post("/api/adm/mailtemplate/insertMailTemplate.do",
                "{\"templateCd\":\"ZZ_DUP\",\"name\":\"복제\",\"subject\":\"제목\",\"content\":\"<p>본문</p>\"}",
                admin).statusCode()).isEqualTo(400);

        deleteTemplate(admin, id);
        // 지운 뒤에는 같은 코드를 다시 쓸 수 있어야 한다(유니크 인덱스는 use_yn='Y'만 대상)
        String again = insertTemplate(admin, "ZZ_DUP", "재사용", "제목", "<p>본문</p>");
        deleteTemplate(admin, again);
    }

    @Test
    @DisplayName("필수값(코드·이름·제목·본문)이 비면 등록되지 않는다")
    void requiredFields() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/mailtemplate/insertMailTemplate.do",
                "{\"templateCd\":\"\",\"name\":\"x\",\"subject\":\"x\",\"content\":\"x\"}", admin).statusCode())
                .isNotEqualTo(200);
        assertThat(post("/api/adm/mailtemplate/insertMailTemplate.do",
                "{\"templateCd\":\"ZZ_REQ\",\"name\":\"x\",\"subject\":\"x\",\"content\":\"\"}", admin).statusCode())
                .isNotEqualTo(200);
    }

    @Test
    @DisplayName("미리보기: 값이 있는 키는 치환되고, 값이 없는 키는 지워지며 경고로 알려준다")
    void previewSubstitutes() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = insertTemplate(admin, "ZZ_PRV", "치환", "안녕 {{name}}", "<p>{{name}} / {{gone}}</p>");

        String body = post("/api/adm/mailtemplate/selectMailTemplateListPreview.do",
                "{\"templateCd\":\"ZZ_PRV\",\"vars\":{\"name\":\"홍길동\"}}", admin).body();
        assertThat(JsonPath.<String>read(body, "$.data.subject")).isEqualTo("안녕 홍길동");
        assertThat(JsonPath.<String>read(body, "$.data.content")).isEqualTo("<p>홍길동 / </p>");
        assertThat(JsonPath.<String>read(body, "$.data.missing")).contains("gone");

        deleteTemplate(admin, id);
    }

    @Test
    @DisplayName("치환 값은 HTML 이스케이프된다 — 빼먹으면 회원 이름이 그대로 메일 본문 스크립트가 된다")
    void varsAreHtmlEscaped() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = insertTemplate(admin, "ZZ_ESC", "이스케이프", "제목", "<p>{{name}}</p>");

        String body = post("/api/adm/mailtemplate/selectMailTemplateListPreview.do",
                "{\"templateCd\":\"ZZ_ESC\",\"vars\":{\"name\":\"<script>alert(1)</script>\"}}", admin).body();
        String content = JsonPath.read(body, "$.data.content");
        assertThat(content).doesNotContain("<script>");
        assertThat(content).contains("&lt;script&gt;");

        deleteTemplate(admin, id);
    }

    @Test
    @DisplayName("발송하면 이력이 남고, 수신자는 암호화 저장돼도 목록에서 검색된다")
    void sendWritesLog() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = insertTemplate(admin, "ZZ_SEND", "발송", "[알림] {{title}}", "<p>{{title}}</p>");

        // mail.enabled=false → 실제로 보내지 않으므로 false를 돌려준다. 그래도 이력은 남아야 한다.
        String res = post("/api/adm/maillog/insertMailLog.do",
                "{\"templateCd\":\"ZZ_SEND\",\"toEmail\":\"zz-mail@example.com\",\"vars\":{\"title\":\"점검안내\"}}",
                admin).body();
        assertThat(JsonPath.<Boolean>read(res, "$.data")).isFalse();

        String logs = post("/api/adm/maillog/selectMailLogList.do",
                "{\"filterKeyword\":\"zz-mail@example.com\",\"pageNo\":1,\"pageSize\":10}", admin).body();
        assertThat(JsonPath.<Integer>read(logs, "$.data.totalCount")).isGreaterThan(0);
        assertThat(JsonPath.<List<String>>read(logs, "$.data.list[*].toEmail")).contains("zz-mail@example.com");
        assertThat(JsonPath.<List<String>>read(logs, "$.data.list[*].statusCd")).contains("SKIP");
        assertThat(JsonPath.<List<String>>read(logs, "$.data.list[*].subject")).contains("[알림] 점검안내");
        // 평문으로 저장하면 로그 한 번 유출에 주소록이 통째로 샌다
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM mail_log WHERE to_email = 'zz-mail@example.com'", Integer.class)).isZero();

        deleteTemplate(admin, id);
        jdbc.update("DELETE FROM mail_log WHERE template_cd = 'ZZ_SEND'");
    }

    @Test
    @DisplayName("없는 템플릿으로 보내면 실패 이력이 남는다 — 조용히 지나가면 아무도 모른다")
    void missingTemplateLogsFailure() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        post("/api/adm/maillog/insertMailLog.do",
                "{\"templateCd\":\"ZZ_NOPE\",\"toEmail\":\"zz-nope@example.com\"}", admin);

        String status = jdbc.queryForObject(
                "SELECT status_cd FROM mail_log WHERE template_cd = 'ZZ_NOPE' ORDER BY mail_log_id DESC LIMIT 1",
                String.class);
        assertThat(status).isEqualTo("FAIL");

        jdbc.update("DELETE FROM mail_log WHERE template_cd = 'ZZ_NOPE'");
    }

    @Test
    @DisplayName("보존기간 지난 이력은 정리된다 — 본문에 인증번호가 남으므로 무한 보관하지 않는다")
    void purgeRemovesOldLogs() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = insertTemplate(admin, "ZZ_OLD", "보존", "제목", "<p>본문</p>");
        // 이력은 정상 경로로 만든다 — 수신자 컬럼을 직접 INSERT하면 암호화 형식이 깨져
        // 이후 다른 목록 조회의 복호화까지 함께 실패한다.
        post("/api/adm/maillog/insertMailLog.do",
                "{\"templateCd\":\"ZZ_OLD\",\"toEmail\":\"zz-old@example.com\"}", admin);
        jdbc.update("UPDATE mail_log SET reg_dt = NOW() - INTERVAL '400 days' WHERE template_cd = 'ZZ_OLD'");

        assertThat(mailService.purgeLogs(90)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_log WHERE template_cd = 'ZZ_OLD'", Integer.class))
                .isZero();
        // 0이면 정리하지 않는다(보관 정책을 직접 관리하는 경우)
        assertThat(mailService.purgeLogs(0)).isZero();

        deleteTemplate(admin, id);
    }

    @Test
    @DisplayName("가입 인증 메일도 템플릿·이력을 탄다 — 직접 JavaMailSender를 부르면 이 이력이 비어 있다")
    void signupCodeGoesThroughTemplate() throws Exception {
        // mail.enabled=false라 실제로 나가지 않고 발송은 실패로 끝나지만(=코드 발급도 롤백),
        // 이력은 독립 트랜잭션이라 반드시 남는다.
        post("/api/auth/sendSignupCode", "{\"email\":\"zz-signup@example.com\"}", null);

        String subject = jdbc.queryForObject(
                "SELECT subject FROM mail_log WHERE template_cd = 'SIGNUP_CODE' ORDER BY mail_log_id DESC LIMIT 1",
                String.class);
        assertThat(subject).isEqualTo("[취만사] 회원가입 인증번호");

        // 본문의 {{code}}·{{ttl}}이 실제 값으로 치환됐는지(마커가 그대로 남으면 수신자가 그걸 본다)
        String content = jdbc.queryForObject(
                "SELECT content FROM mail_log WHERE template_cd = 'SIGNUP_CODE' ORDER BY mail_log_id DESC LIMIT 1",
                String.class);
        assertThat(content).doesNotContain("{{code}}").doesNotContain("{{ttl}}");
        assertThat(content).contains("5분간");

        jdbc.update("DELETE FROM mail_log WHERE template_cd = 'SIGNUP_CODE'");
        jdbc.update("DELETE FROM email_verification WHERE target = 'zz-signup@example.com'");
    }

    @Test
    @DisplayName("메일 관리 API는 비로그인에게 열려 있지 않다")
    void guestBlocked() throws Exception {
        assertThat(post("/api/adm/mailtemplate/selectMailTemplateList.do", "{}", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/adm/maillog/selectMailLogList.do", "{}", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/adm/maillog/insertMailLog.do",
                "{\"templateCd\":\"WELCOME\",\"toEmail\":\"x@example.com\"}", null).statusCode()).isEqualTo(401);
    }

    // ===== helpers =====

    private String insertTemplate(String token, String cd, String name, String subject, String content)
            throws Exception {
        assertThat(post("/api/adm/mailtemplate/insertMailTemplate.do",
                "{\"templateCd\":\"" + cd + "\",\"name\":\"" + name + "\",\"subject\":\"" + subject
                        + "\",\"content\":\"" + content + "\"}", token).statusCode()).isEqualTo(200);
        return String.valueOf(jdbc.queryForObject(
                "SELECT MAX(mail_template_id) FROM mail_template WHERE template_cd = ?", Integer.class, cd));
    }

    private void deleteTemplate(String token, String rowId) throws Exception {
        post("/api/adm/mailtemplate/deleteMailTemplate.do", "{\"rowId\":\"" + rowId + "\"}", token);
    }

    private List<String> templateCodes(String token) throws Exception {
        String body = post("/api/adm/mailtemplate/selectMailTemplateList.do",
                "{\"pageNo\":1,\"pageSize\":100}", token).body();
        return JsonPath.read(body, "$.data.list[*].templateCd");
    }
}
