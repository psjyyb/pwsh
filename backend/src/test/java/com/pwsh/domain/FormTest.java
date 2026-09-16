package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 폼(신청·민원·설문) — 정의 · 제출 · 집계 검증.
 *
 * <p>여기서 고정하려는 것은 "화면이 뜬다"가 아니라 <b>서버가 혼자서도 막는가</b>다.
 * 폼 응답은 화면 없이도 요청을 만들 수 있어서, 기간·로그인·중복·필수·선택지 검증이
 * 프론트에만 있으면 없는 것과 같다. 개인정보 문항의 저장 형태(암호화)와 접근기록 연동도 함께 본다.
 */
class FormTest extends IntegrationTest {

    @Test
    @DisplayName("폼을 등록하면 문항이 순서대로 붙고, 뺀 문항은 비활성화된다")
    void formAndFieldsAreSaved() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ설문", "FORM03",
                field("만족도", "FIELD03", "좋음\n보통\n나쁨", "Y", "N")
                        + "," + field("의견", "FIELD02", null, "N", "N"));

        List<String> labels = JsonPath.read(view(admin, formId), "$.data.fields[*].label");
        assertThat(labels).containsExactly("만족도", "의견");

        // 문항 하나만 남기고 저장 → 나머지는 사라진다(물리 삭제가 아니라 비활성화)
        String keepId = JsonPath.<List<String>>read(view(admin, formId), "$.data.fields[*].rowId").get(0);
        assertThat(post("/api/adm/form/updateForm.do",
                "{\"rowId\":\"" + formId + "\",\"title\":\"ZZ설문\",\"typeCd\":\"FORM03\",\"fields\":["
                        + "{\"rowId\":\"" + keepId + "\",\"label\":\"만족도\",\"fieldCd\":\"FIELD03\","
                        + "\"options\":\"좋음\\n보통\\n나쁨\",\"requiredYn\":\"Y\"}]}", admin).statusCode())
                .isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(view(admin, formId), "$.data.fields[*].label"))
                .containsExactly("만족도");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM form_field WHERE form_id = ?::integer", Integer.class, formId))
                .as("비활성화일 뿐 행은 남아야 한다 — 지우면 과거 응답이 어느 문항의 답인지 알 수 없다")
                .isEqualTo(2);

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("제출한 답이 상세 조회에 그대로 나온다")
    void submitAndRead() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ제출", "FORM03",
                field("만족도", "FIELD03", "좋음\n보통\n나쁨", "Y", "N"));
        String fieldId = firstFieldId(admin, formId);

        String answerId = submitValue(admin, formId, fieldId, "보통");

        String detail = post("/api/adm/formanswer/selectFormAnswerView.do",
                "{\"rowId\":\"" + answerId + "\"}", admin).body();
        assertThat(JsonPath.<String>read(detail, "$.data.values." + fieldId)).isEqualTo("보통");
        assertThat(JsonPath.<String>read(detail, "$.data.statusCd")).isEqualTo("ANSWER01");

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("개인정보 문항의 답은 암호화 저장되고, 열어 보면 접근기록이 남는다")
    void privacyFieldIsEncryptedAndLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ신청", "FORM01",
                field("연락처", "FIELD01", null, "Y", "Y"));
        String fieldId = firstFieldId(admin, formId);

        String answerId = submitValue(admin, formId, fieldId, "010-1234-5678");

        // 평문으로 남으면 개인정보가 그대로 DB에 눕는다
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM form_answer_value WHERE form_answer_id = ?::integer AND value = '010-1234-5678'",
                Integer.class, answerId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM form_answer_value WHERE form_answer_id = ?::integer AND value_enc IS NOT NULL",
                Integer.class, answerId)).isEqualTo(1);

        jdbc.update("DELETE FROM privacy_log");
        String detail = post("/api/adm/formanswer/selectFormAnswerView.do",
                "{\"rowId\":\"" + answerId + "\"}", admin).body();
        assertThat(JsonPath.<String>read(detail, "$.data.values." + fieldId)).isEqualTo("010-1234-5678");
        assertThat(awaitPrivacyLogCount()).as("개인정보 문항을 열었으면 접근기록이 남아야 한다").isPositive();

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("개인정보 문항이 없는 설문은 열어 봐도 접근기록을 만들지 않는다")
    void plainFormDoesNotWritePrivacyLog() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ무개인정보", "FORM03",
                field("만족도", "FIELD03", "좋음\n보통", "Y", "N"));
        String fieldId = firstFieldId(admin, formId);
        String answerId = submitValue(admin, formId, fieldId, "좋음");

        Thread.sleep(300);
        jdbc.update("DELETE FROM privacy_log");
        post("/api/adm/formanswer/selectFormAnswerView.do", "{\"rowId\":\"" + answerId + "\"}", admin);
        Thread.sleep(500);
        // 항상 복호화를 걸면 개인정보가 하나도 없는 설문을 봐도 기록이 쌓여 접근기록이 무의미해진다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM privacy_log", Integer.class)).isZero();

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("필수 누락·선택지에 없는 값은 서버가 거부한다(화면 없이 보낸 요청)")
    void serverValidatesAnswers() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ검증", "FORM03",
                field("만족도", "FIELD03", "좋음\n보통", "Y", "N"));
        String fieldId = firstFieldId(admin, formId);

        assertThat(post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + formId + "\",\"values\":{}}", admin).statusCode())
                .as("필수 문항 누락").isEqualTo(400);
        assertThat(post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + formId + "\",\"values\":{\"" + fieldId + "\":\"아주좋음\"}}", admin).statusCode())
                .as("선택지에 없는 값").isEqualTo(400);

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("접수 기간이 지난 폼은 제출되지 않는다")
    void closedFormRejectsSubmit() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ마감", "FORM01", field("내용", "FIELD01", null, "N", "N"));
        jdbc.update("UPDATE form SET start_dt = '2000-01-01', end_dt = '2000-12-31' WHERE form_id = ?::integer",
                Integer.parseInt(formId));

        assertThat(post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + formId + "\",\"values\":{}}", admin).statusCode()).isEqualTo(400);

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("중복 제출은 1인 1회 폼에서만 막힌다")
    void duplicateSubmitBlockedUnlessAllowed() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ중복", "FORM01", field("내용", "FIELD01", null, "N", "N"));

        assertThat(submitEmpty(admin, formId).statusCode()).isEqualTo(200);
        assertThat(submitEmpty(admin, formId).statusCode()).as("기본은 1인 1회").isEqualTo(400);

        jdbc.update("UPDATE form SET multi_yn = 'Y' WHERE form_id = ?::integer", Integer.parseInt(formId));
        assertThat(submitEmpty(admin, formId).statusCode()).as("허용하면 여러 번 낼 수 있다").isEqualTo(200);

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("로그인 필요 폼은 비로그인 제출을 막고, 허용 폼은 게스트도 낼 수 있다")
    void loginRequirementIsEnforced() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ게스트", "FORM02", field("내용", "FIELD01", null, "N", "N"));
        openToGuest(formId); // GEN 메뉴(MENU05) + GUEST 권한 — 없으면 게스트는 폼 자체를 못 연다

        assertThat(submitEmpty(null, formId).statusCode()).as("기본은 로그인 필요").isEqualTo(401);

        jdbc.update("UPDATE form SET login_yn = 'N' WHERE form_id = ?::integer", Integer.parseInt(formId));
        assertThat(submitEmpty(null, formId).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "SELECT member_id FROM form_answer WHERE form_id = ?::integer ORDER BY form_answer_id DESC LIMIT 1",
                String.class, Integer.parseInt(formId)))
                .as("비로그인 제출은 제출자가 없다").isNull();

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("메뉴에 걸리지 않은 폼은 사용자가 ID로 직접 열 수 없다")
    void deepLinkBlockedWithoutMenu() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");
        String formId = insertForm(admin, "ZZ비공개", "FORM01", field("내용", "FIELD01", null, "N", "N"));

        assertThat(post("/api/adm/form/selectFormView.do", "{\"rowId\":\"" + formId + "\"}", user).statusCode())
                .isEqualTo(403);
        assertThat(submitEmpty(user, formId).statusCode()).isEqualTo(403);

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("문항별 집계 — 선택지별 응답 수(선택 안 된 보기도 0으로 남는다)")
    void statsCountOptions() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ집계", "FORM03",
                field("만족도", "FIELD03", "좋음\n보통\n나쁨", "Y", "N"));
        String fieldId = firstFieldId(admin, formId);
        jdbc.update("UPDATE form SET multi_yn = 'Y' WHERE form_id = ?::integer", Integer.parseInt(formId));

        submitValue(admin, formId, fieldId, "좋음");
        submitValue(admin, formId, fieldId, "좋음");
        submitValue(admin, formId, fieldId, "보통");

        String body = post("/api/adm/formanswer/selectFormAnswerListStats.do",
                "{\"formId\":\"" + formId + "\"}", admin).body();
        assertThat(JsonPath.<Integer>read(body, "$.data[0].answerCnt")).isEqualTo(3);
        List<Map<String, Object>> options = JsonPath.read(body, "$.data[0].options");
        assertThat(options).extracting(o -> o.get("label")).containsExactly("좋음", "보통", "나쁨");
        assertThat(options).extracting(o -> o.get("cnt")).containsExactly(2, 1, 0);

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("처리상태·담당자 메모를 바꿀 수 있고, 응답 내용은 바뀌지 않는다")
    void statusCanBeChanged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ상태", "FORM02", field("내용", "FIELD01", null, "N", "N"));
        String fieldId = firstFieldId(admin, formId);
        String answerId = submitValue(admin, formId, fieldId, "원본");

        assertThat(post("/api/adm/formanswer/updateFormAnswerStatus.do",
                "{\"rowId\":\"" + answerId + "\",\"statusCd\":\"ANSWER03\",\"adminMemo\":\"처리함\"}", admin)
                .statusCode()).isEqualTo(200);

        String detail = post("/api/adm/formanswer/selectFormAnswerView.do",
                "{\"rowId\":\"" + answerId + "\"}", admin).body();
        assertThat(JsonPath.<String>read(detail, "$.data.statusCd")).isEqualTo("ANSWER03");
        assertThat(JsonPath.<String>read(detail, "$.data.adminMemo")).isEqualTo("처리함");
        assertThat(JsonPath.<String>read(detail, "$.data.values." + fieldId)).isEqualTo("원본");

        cleanup(admin, formId);
    }

    @Test
    @DisplayName("폼을 지우면 문항과 응답도 함께 내려간다")
    void deleteCascades() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String formId = insertForm(admin, "ZZ삭제", "FORM01", field("내용", "FIELD01", null, "N", "N"));
        submitEmpty(admin, formId);

        assertThat(post("/api/adm/form/deleteForm.do", "{\"rowId\":\"" + formId + "\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM form_field WHERE form_id = ?::integer AND use_yn = 'Y'",
                Integer.class, Integer.parseInt(formId))).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM form_answer WHERE form_id = ?::integer AND use_yn = 'Y'",
                Integer.class, Integer.parseInt(formId))).isZero();

        jdbc.update("DELETE FROM form_answer_value WHERE form_answer_id IN"
                + " (SELECT form_answer_id FROM form_answer WHERE form_id = ?::integer)", Integer.parseInt(formId));
        jdbc.update("DELETE FROM form_answer WHERE form_id = ?::integer", Integer.parseInt(formId));
        jdbc.update("DELETE FROM form_field WHERE form_id = ?::integer", Integer.parseInt(formId));
        jdbc.update("DELETE FROM form WHERE form_id = ?::integer", Integer.parseInt(formId));
    }

    // ===== helpers =====

    private String field(String label, String fieldCd, String options, String requiredYn, String privacyYn) {
        return "{\"label\":\"" + label + "\",\"fieldCd\":\"" + fieldCd + "\""
                + (options == null ? "" : ",\"options\":\"" + options.replace("\n", "\\n") + "\"")
                + ",\"requiredYn\":\"" + requiredYn + "\",\"privacyYn\":\"" + privacyYn + "\"}";
    }

    private String insertForm(String token, String title, String typeCd, String fieldsJson) throws Exception {
        assertThat(post("/api/adm/form/insertForm.do",
                "{\"title\":\"" + title + "\",\"typeCd\":\"" + typeCd + "\",\"fields\":[" + fieldsJson + "]}",
                token).statusCode()).isEqualTo(200);
        return String.valueOf(jdbc.queryForObject(
                "SELECT MAX(form_id) FROM form WHERE title = ?", Integer.class, title));
    }

    private String view(String token, String formId) throws Exception {
        return post("/api/adm/form/selectFormView.do", "{\"rowId\":\"" + formId + "\"}", token).body();
    }

    private String firstFieldId(String token, String formId) throws Exception {
        return JsonPath.<List<String>>read(view(token, formId), "$.data.fields[*].rowId").get(0);
    }

    private java.net.http.HttpResponse<String> submitEmpty(String token, String formId) throws Exception {
        return post("/api/adm/formanswer/insertFormAnswer.do", "{\"formId\":\"" + formId + "\"}", token);
    }

    /** 제출(값 1개) 후 생성된 응답 ID. 실패하면 응답 본문을 그대로 보여준다(원인 추적용). */
    private String submitValue(String token, String formId, String fieldId, String value) throws Exception {
        var res = post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + formId + "\",\"values\":{\"" + fieldId + "\":\"" + value + "\"}}", token);
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return JsonPath.read(res.body(), "$.data");
    }

    /** GEN 메뉴(연결유형 MENU05)에 걸고 GUEST·MEMBER 권한을 준다 — 사용자가 폼을 열 수 있는 상태. */
    private void openToGuest(String formId) {
        jdbc.update("INSERT INTO menu (p_menu_id, area, name, sort_no, conn_cd, conn_id, target_yn, use_yn,"
                + " reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (0, 'GEN', 'ZZ폼메뉴', 99, 'MENU05', ?::integer, 'N', 'Y',"
                + " 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", Integer.parseInt(formId));
        jdbc.update("INSERT INTO auth (menu_id, conn_id, type, menu_yn, search_yn, mod_yn, use_yn,"
                + " reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " SELECT menu_id, g.grp, 'GRP', 'Y', 'Y', 'N', 'Y', 'system', 'system',"
                + " NOW(), NOW(), '127.0.0.1', '127.0.0.1'"
                + " FROM menu, (VALUES ('GUEST'), ('MEMBER')) AS g(grp)"
                + " WHERE conn_cd = 'MENU05' AND conn_id = ?::integer", Integer.parseInt(formId));
    }

    private int awaitPrivacyLogCount() throws Exception {
        for (int i = 0; i < 40; i++) {
            Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM privacy_log", Integer.class);
            if (cnt != null && cnt > 0) {
                return cnt;
            }
            Thread.sleep(50);
        }
        return 0;
    }

    /** 테스트가 만든 폼·문항·응답·메뉴를 남기지 않는다. */
    private void cleanup(String token, String formId) throws Exception {
        int id = Integer.parseInt(formId);
        jdbc.update("DELETE FROM form_answer_value WHERE form_answer_id IN"
                + " (SELECT form_answer_id FROM form_answer WHERE form_id = ?)", id);
        jdbc.update("DELETE FROM form_answer WHERE form_id = ?", id);
        jdbc.update("DELETE FROM form_field WHERE form_id = ?", id);
        jdbc.update("DELETE FROM auth WHERE menu_id IN"
                + " (SELECT menu_id FROM menu WHERE conn_cd = 'MENU05' AND conn_id = ?)", id);
        jdbc.update("DELETE FROM menu WHERE conn_cd = 'MENU05' AND conn_id = ?", id);
        jdbc.update("DELETE FROM form WHERE form_id = ?", id);
        jdbc.update("DELETE FROM privacy_log");
    }
}
