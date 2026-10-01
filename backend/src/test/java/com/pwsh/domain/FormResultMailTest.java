package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 신청 결과 메일 자동발송.
 *
 * <p>접수·처리는 되는데 신청자에게 알릴 방법이 없었다 — 관리자가 상태를 바꿔도 신청자는
 * 다시 들어와 확인할 화면조차 없다.
 *
 * <p>★ 조용히 틀릴 수 있는 곳이 둘이다.
 * <ul>
 *   <li>수신 주소를 <b>회원 이메일에서만</b> 찾으면 비로그인 제출 건은 영영 통지가 안 간다.</li>
 *   <li>메일 실패가 상태 변경을 <b>되돌리면</b> 관리자는 처리했는데 화면은 그대로가 된다.</li>
 * </ul>
 */
class FormResultMailTest extends IntegrationTest {

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM mail_log WHERE template_cd = 'FORM_RESULT'");
        jdbc.update("DELETE FROM form_answer_value WHERE form_answer_id IN"
                + " (SELECT form_answer_id FROM form_answer WHERE form_id IN"
                + "  (SELECT form_id FROM form WHERE title LIKE 'ZZ메일%'))");
        jdbc.update("DELETE FROM form_answer WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ메일%')");
        jdbc.update("DELETE FROM form_field WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ메일%')");
        jdbc.update("DELETE FROM form WHERE title LIKE 'ZZ메일%'");
    }

    @Test
    @DisplayName("결과 메일을 켠 폼은 상태를 바꾸면 신청자에게 메일이 나간다")
    void sendsOnStatusChange() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newForm(admin, "ZZ메일 발송", "Y");
        String answerId = submit(admin, ids[0], ids[1], "hong@example.com");

        assertThat(post("/api/adm/formanswer/updateFormAnswerStatus.do",
                "{\"rowId\":\"" + answerId + "\",\"statusCd\":\"ANSWER03\",\"adminMemo\":\"승인되었습니다\"}",
                admin).statusCode()).isEqualTo(200);

        String subject = lastMail("subject");
        assertThat(subject).contains("ZZ메일 발송");
        String content = lastMail("content");
        // 마커가 남으면 수신자가 {{statusName}}을 그대로 보게 된다
        assertThat(content).doesNotContain("{{").contains("완료").contains("승인되었습니다");
    }

    @Test
    @DisplayName("결과 메일을 끈 폼은 상태를 바꿔도 메일이 안 나간다")
    void skipsWhenDisabled() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newForm(admin, "ZZ메일 꺼짐", "N");
        String answerId = submit(admin, ids[0], ids[1], "hong@example.com");

        post("/api/adm/formanswer/updateFormAnswerStatus.do",
                "{\"rowId\":\"" + answerId + "\",\"statusCd\":\"ANSWER03\"}", admin);

        assertThat(mailCount()).as("설문까지 메일이 나가면 안 된다").isZero();
    }

    @Test
    @DisplayName("비로그인 제출도 이메일 문항 답으로 통지가 간다")
    void usesEmailFieldForAnonymousAnswer() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newForm(admin, "ZZ메일 비로그인", "Y");
        // 제출자를 비워 비로그인 제출과 같은 상태로 만든다(폼 자체는 로그인 필수라 API로는 못 만든다)
        String answerId = submit(admin, ids[0], ids[1], "anon@example.com");
        jdbc.update("UPDATE form_answer SET member_id = NULL WHERE form_answer_id = ?::integer",
                Integer.parseInt(answerId));

        post("/api/adm/formanswer/updateFormAnswerStatus.do",
                "{\"rowId\":\"" + answerId + "\",\"statusCd\":\"ANSWER04\",\"adminMemo\":\"서류 미비\"}", admin);

        // ★ 회원 이메일만 보면 이 건은 영영 통지가 안 간다.
        //   수신 주소는 mail_log에 암호화 저장되므로 관리자 목록 API(복호화 경로)로 확인한다.
        String logs = post("/api/adm/maillog/selectMailLogList.do",
                "{\"filterKeyword\":\"anon@example.com\"}", admin).body();
        assertThat((int) JsonPath.read(logs, "$.data.totalCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("수신 주소가 없으면 조용히 건너뛰되 상태 변경은 남는다")
    void noAddressStillChangesStatus() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newForm(admin, "ZZ메일 주소없음", "Y");
        String answerId = submit(admin, ids[0], ids[1], "drop@example.com");
        // 회원 이메일도 없고 이메일 문항 답도 없는 상태
        jdbc.update("UPDATE form_answer SET member_id = NULL WHERE form_answer_id = ?::integer",
                Integer.parseInt(answerId));
        jdbc.update("DELETE FROM form_answer_value WHERE form_answer_id = ?::integer",
                Integer.parseInt(answerId));

        assertThat(post("/api/adm/formanswer/updateFormAnswerStatus.do",
                "{\"rowId\":\"" + answerId + "\",\"statusCd\":\"ANSWER03\"}", admin).statusCode())
                .as("메일을 못 보내도 처리 자체는 성공해야 한다").isEqualTo(200);

        assertThat(mailCount()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status_cd FROM form_answer WHERE form_answer_id = ?::integer",
                String.class, Integer.parseInt(answerId))).isEqualTo("ANSWER03");
    }

    // ===== helpers =====

    /** 이메일 문항 하나짜리 폼 → [formId, fieldId] */
    private String[] newForm(String token, String title, String resultMailYn) throws Exception {
        assertThat(post("/api/adm/form/insertForm.do",
                "{\"title\":\"" + title + "\",\"typeCd\":\"FORM01\",\"loginYn\":\"Y\",\"multiYn\":\"Y\","
                        + "\"resultMailYn\":\"" + resultMailYn + "\","
                        + "\"fields\":[{\"label\":\"이메일\",\"fieldCd\":\"FIELD08\",\"requiredYn\":\"N\"}]}",
                token).statusCode()).isEqualTo(200);
        String formId = jdbc.queryForObject("SELECT form_id::text FROM form WHERE title = ?", String.class, title);
        String fieldId = jdbc.queryForObject(
                "SELECT form_field_id::text FROM form_field WHERE form_id = ?::integer AND use_yn = 'Y'",
                String.class, formId);
        return new String[] {formId, fieldId};
    }

    private String submit(String token, String formId, String fieldId, String email) throws Exception {
        String answerId = JsonPath.read(post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + formId + "\",\"values\":{\"" + fieldId + "\":\"" + email + "\"}}",
                token).body(), "$.data");
        return answerId;
    }

    private String lastMail(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM mail_log WHERE template_cd = 'FORM_RESULT'"
                + " ORDER BY mail_log_id DESC LIMIT 1", String.class);
    }

    private int mailCount() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mail_log WHERE template_cd = 'FORM_RESULT'", Integer.class);
        return n == null ? 0 : n;
    }
}
