package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 폼 첨부 열람의 개인정보 접근기록.
 *
 * <p>★ 개인정보 접근로그는 실행 SQL의 {@code DECRYPT}를 보고 자동 탐지한다. 그런데 <b>첨부 파일은
 * 암호화 컬럼이 아니라 그 그물에 통째로 걸리지 않는다</b> — 신청서에 주민등록등본을 받아도
 * 누가 열람했는지 기록이 남지 않았다. 그래서 파일을 내려주는 지점에서 직접 넣는다.
 *
 * <p>조용히 틀릴 수 있는 곳이 둘이다.
 * <ul>
 *   <li>기록이 아예 안 남는 것 — 에러가 없어서 한참 모른다.</li>
 *   <li>게시판 첨부·로고까지 남는 것 — 정작 봐야 할 서류 열람이 묻힌다.</li>
 * </ul>
 */
class FormAttachmentPrivacyLogTest extends IntegrationTest {

    private static final byte[] PNG = new byte[] {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R',
    };

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM privacy_log WHERE sql_ids LIKE '%downloadFormAttachment%'"
                + " OR sql_ids LIKE '%exportFormAttachmentNames%'");
        jdbc.update("DELETE FROM form_answer_value WHERE form_answer_id IN"
                + " (SELECT form_answer_id FROM form_answer WHERE form_id IN"
                + "  (SELECT form_id FROM form WHERE title LIKE 'ZZ기록%'))");
        jdbc.update("DELETE FROM file_ref WHERE map_key IN"
                + " (SELECT form_answer_id FROM form_answer WHERE form_id IN"
                + "  (SELECT form_id FROM form WHERE title LIKE 'ZZ기록%'))");
        jdbc.update("DELETE FROM form_answer WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ기록%')");
        jdbc.update("DELETE FROM form_field WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ기록%')");
        jdbc.update("DELETE FROM form WHERE title LIKE 'ZZ기록%'");
        jdbc.update("DELETE FROM file_ref WHERE file_id IN (SELECT file_id FROM file WHERE original_name LIKE 'zzpl%')");
        jdbc.update("UPDATE file SET use_yn = 'Y', reg_dt = NOW() - INTERVAL '48 hours'"
                + " WHERE original_name LIKE 'zzpl%'");
    }

    @Test
    @DisplayName("폼 첨부를 내려받으면 신청자를 정보주체로 접근기록이 남는다")
    void downloadingFormAttachmentIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String fileId = submitWithAttachment(admin, "ZZ기록 다운로드", "zzpl-doc.png");

        assertThat(get("/api/adm/file/download.do?fileId=" + fileId, admin).statusCode()).isEqualTo(200);

        String targets = jdbc.queryForObject(
                "SELECT target_ids FROM privacy_log WHERE sql_ids LIKE '%downloadFormAttachment%'"
                        + " ORDER BY privacy_log_id DESC LIMIT 1", String.class);
        assertThat(targets).as("누구 서류를 봤는지가 남아야 한다").isEqualTo("user");
    }

    @Test
    @DisplayName("공개 이미지 경로로 열어도 똑같이 기록된다")
    void viewingViaPubImageIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String fileId = submitWithAttachment(admin, "ZZ기록 이미지", "zzpl-img.png");

        // 첨부가 이미지면 이 경로로도 열람된다(FORM은 공개 용도가 아니라 관리자만 통과)
        assertThat(get("/api/pub/image/" + fileId, admin).statusCode()).isEqualTo(200);

        assertThat(logCount()).as("다운로드만 막고 이 경로를 비워두면 기록을 우회할 수 있다").isEqualTo(1);
    }

    @Test
    @DisplayName("게시판 첨부 열람은 기록하지 않는다")
    void boardAttachmentIsNotLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String fileId = upload(admin, "zzpl-board.png");
        String postId = JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"ZZ기록 게시글\",\"content\":\"본문\"}", admin).body(), "$.data");
        post("/api/adm/file/saveFileMapping.do",
                "{\"mapKey\":\"" + postId + "\",\"fileType\":\"POST\",\"fileIds\":[\"" + fileId + "\"]}", admin);

        assertThat(get("/api/adm/file/download.do?fileId=" + fileId, admin).statusCode()).isEqualTo(200);

        // 게시판 첨부·로고까지 남기면 정작 봐야 할 서류 열람이 묻힌다
        assertThat(logCount()).isZero();

        jdbc.update("DELETE FROM file_ref WHERE map_key = ?::integer", Integer.parseInt(postId));
        jdbc.update("DELETE FROM post WHERE post_id = ?::integer", Integer.parseInt(postId));
    }

    @Test
    @DisplayName("map_key가 같아도 용도가 FORM이 아니면 기록하지 않는다")
    void mapKeyCollisionDoesNotMisattribute() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        submitWithAttachment(admin, "ZZ기록 충돌", "zzpl-form.png");
        String answerId = jdbc.queryForObject(
                "SELECT form_answer_id::text FROM form_answer WHERE form_id ="
                        + " (SELECT form_id FROM form WHERE title = 'ZZ기록 충돌')", String.class);

        // ★ file_ref.map_key는 다형적이다(file_type에 따라 post_id일 수도, form_answer_id일 수도).
        //   용도로 걸러내지 않으면 게시글 첨부의 map_key가 어쩌다 응답 번호와 같을 때
        //   "엉뚱한 사람 서류를 열람했다"고 기록된다. 그 상황을 일부러 만들어 확인한다.
        String otherFile = upload(admin, "zzpl-other.png");
        jdbc.update("INSERT INTO file_ref (map_key, file_id, file_type, sort_no)"
                + " VALUES (?::integer, ?::integer, 'POST', 0)", answerId, otherFile);

        assertThat(get("/api/adm/file/download.do?fileId=" + otherFile, admin).statusCode()).isEqualTo(200);

        assertThat(logCount()).as("용도 필터가 빠지면 남의 응답 번호로 기록이 붙는다").isZero();
    }

    @Test
    @DisplayName("파일첨부 문항만 있는 폼도 응답 내려받기가 접근기록에 남는다")
    void exportOfFileOnlyFormIsLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        submitWithAttachment(admin, "ZZ기록 내려받기", "zzpl-export.png");
        String formId = formIdOf("ZZ기록 내려받기");

        assertThat(export(formId, admin).statusCode()).isEqualTo(200);

        // ★ 내려받기에는 파일명이 나가고(1.2.16) 파일명엔 신청자 이름이 흔히 들어간다.
        //   개인정보 문항이 없으면 복호화가 없어 자동 탐지에 안 걸린다 — 직접 남겨야 한다
        assertThat(exportLogTargets()).as("누구 서류 목록을 받았는지가 남아야 한다").isEqualTo("user");
    }

    @Test
    @DisplayName("비로그인 제출은 응답 번호로 대상을 남긴다")
    void exportOfAnonymousAnswerUsesAnswerNo() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        submitWithAttachment(admin, "ZZ기록 비로그인", "zzpl-anon.png");
        String formId = formIdOf("ZZ기록 비로그인");
        String answerId = jdbc.queryForObject(
                "SELECT form_answer_id::text FROM form_answer WHERE form_id = ?::integer", String.class, formId);
        jdbc.update("UPDATE form_answer SET member_id = NULL WHERE form_answer_id = ?::integer",
                Integer.parseInt(answerId));

        assertThat(export(formId, admin).statusCode()).isEqualTo(200);

        // 대상을 비우면 "(대상 불명)"이 돼 누구 서류였는지 추적할 수 없다
        assertThat(exportLogTargets()).isEqualTo("응답#" + answerId);
    }

    @Test
    @DisplayName("첨부가 하나도 없으면 내려받기를 기록하지 않는다")
    void exportWithoutAttachmentsIsNotLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/form/insertForm.do",
                "{\"title\":\"ZZ기록 첨부없음\",\"typeCd\":\"FORM01\",\"loginYn\":\"Y\",\"multiYn\":\"Y\","
                        + "\"fields\":[{\"label\":\"서류\",\"fieldCd\":\"FIELD09\",\"requiredYn\":\"N\"}]}",
                admin).statusCode()).isEqualTo(200);
        String formId = formIdOf("ZZ기록 첨부없음");
        assertThat(post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + formId + "\",\"values\":{}}", admin).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE form_answer SET member_id = 'user' WHERE form_id = ?::integer", Integer.parseInt(formId));

        assertThat(export(formId, admin).statusCode()).isEqualTo(200);

        // 파일 문항이 있다는 것만으로 남기면 설문을 받을 때마다 기록이 쌓여 정작 볼 기록이 묻힌다
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM privacy_log WHERE sql_ids LIKE '%exportFormAttachmentNames%'", Integer.class))
                .isZero();
    }

    // ===== helpers =====

    private HttpResponse<String> export(String formId, String token) throws Exception {
        return post("/api/adm/formanswer/selectFormAnswerListExport.do", "{\"formId\":\"" + formId + "\"}", token);
    }

    private String formIdOf(String title) {
        return jdbc.queryForObject("SELECT form_id::text FROM form WHERE title = ?", String.class, title);
    }

    private String exportLogTargets() {
        return jdbc.queryForObject(
                "SELECT target_ids FROM privacy_log WHERE sql_ids LIKE '%exportFormAttachmentNames%'"
                        + " ORDER BY privacy_log_id DESC LIMIT 1", String.class);
    }

    private int logCount() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM privacy_log WHERE sql_ids LIKE '%downloadFormAttachment%'", Integer.class);
        return n == null ? 0 : n;
    }

    /** 파일첨부 문항 폼을 만들고 파일을 붙여 제출 → 그 파일 ID */
    private String submitWithAttachment(String token, String title, String fileName) throws Exception {
        assertThat(post("/api/adm/form/insertForm.do",
                "{\"title\":\"" + title + "\",\"typeCd\":\"FORM01\",\"loginYn\":\"Y\",\"multiYn\":\"Y\","
                        + "\"fields\":[{\"label\":\"서류\",\"fieldCd\":\"FIELD09\",\"requiredYn\":\"N\"}]}",
                token).statusCode()).isEqualTo(200);
        String formId = jdbc.queryForObject("SELECT form_id::text FROM form WHERE title = ?", String.class, title);
        String fieldId = jdbc.queryForObject(
                "SELECT form_field_id::text FROM form_field WHERE form_id = ?::integer AND use_yn = 'Y'",
                String.class, formId);
        String fileId = upload(token, fileName);
        String answerId = JsonPath.read(post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + formId + "\",\"values\":{\"" + fieldId + "\":\"" + fileId + "\"}}",
                token).body(), "$.data");
        // ★ 제출자를 관리자가 아닌 사람으로 바꾼다. 안 그러면 "관리자가 자기 서류를 본 것"이 되어
        //   본인 조회 제외 필터(summarize의 isSelfOnly)에 걸려 기록이 남지 않는다 —
        //   확인하려는 건 "관리자가 신청자 서류를 열람한 사실"이다.
        //   (폼이 GEN 메뉴에 안 걸려 있어 user 계정으로는 제출 자체가 403이라 이렇게 만든다)
        jdbc.update("UPDATE form_answer SET member_id = 'user' WHERE form_answer_id = ?::integer",
                Integer.parseInt(answerId));
        // 제출 과정에서 생긴 기록과 섞이지 않게 비운다 — 여기서 보려는 건 열람이다
        jdbc.update("DELETE FROM privacy_log WHERE sql_ids LIKE '%downloadFormAttachment%'");
        return fileId;
    }

    private String upload(String token, String name) throws Exception {
        String boundary = "ZZBoundary" + System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + name + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(PNG);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/adm/file/upload.do"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(200);
        String fileId = JsonPath.read(res.body(), "$.data[0].fileId");
        return fileId;
    }
}
