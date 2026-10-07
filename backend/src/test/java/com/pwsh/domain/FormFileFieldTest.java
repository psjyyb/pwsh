package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 폼 파일첨부 문항(FIELD09).
 *
 * <p>답으로 오는 건 <b>파일 ID 숫자뿐</b>이다. 그래서 두 가지가 조용히 틀릴 수 있다.
 * <ul>
 *   <li>남의 파일 ID를 적어 내 응답에 붙이는 것(순차 ID 추측 = IDOR) — 관리자가 응답 상세에서
 *       그 파일을 내려받으므로 곧바로 유출이 된다.</li>
 *   <li>file_ref 매핑을 안 걸어 제출한 서류가 <b>다음 날 고아 파일 GC에 지워지는 것</b>.</li>
 * </ul>
 * 둘 다 에러가 안 나고 화면에도 표시가 없다.
 */
class FormFileFieldTest extends IntegrationTest {

    private static final byte[] PNG = new byte[] {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R',
    };

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM form_answer_value WHERE form_answer_id IN"
                + " (SELECT form_answer_id FROM form_answer WHERE form_id IN"
                + "  (SELECT form_id FROM form WHERE title LIKE 'ZZ첨부%'))");
        jdbc.update("DELETE FROM file_ref WHERE map_key IN"
                + " (SELECT form_answer_id FROM form_answer WHERE form_id IN"
                + "  (SELECT form_id FROM form WHERE title LIKE 'ZZ첨부%'))");
        jdbc.update("DELETE FROM form_answer WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ첨부%')");
        jdbc.update("DELETE FROM form_field WHERE form_id IN (SELECT form_id FROM form WHERE title LIKE 'ZZ첨부%')");
        jdbc.update("DELETE FROM form WHERE title LIKE 'ZZ첨부%'");
        // 업로드된 테스트 파일은 매핑을 끊고 유예 밖으로 민 뒤 GC에 태운다(디스크까지 정리)
        jdbc.update("DELETE FROM file_ref WHERE file_id IN (SELECT file_id FROM file WHERE original_name LIKE 'zzff%')");
        jdbc.update("UPDATE file SET use_yn = 'Y', reg_dt = NOW() - INTERVAL '48 hours'"
                + " WHERE original_name LIKE 'zzff%'");
    }

    @Test
    @DisplayName("파일첨부 문항이 있으면 로그인 필수 폼이어야 저장된다")
    void fileFieldRequiresLoginForm() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 업로드가 인증을 요구하므로, 비로그인 폼에 파일 문항을 두면 제출 직전에야 실패한다
        assertThat(saveForm(admin, "ZZ첨부 비로그인", "N", "FIELD09").statusCode())
                .as("저장 시점에 막아야 한다").isEqualTo(400);
        assertThat(saveForm(admin, "ZZ첨부 로그인", "Y", "FIELD09").statusCode()).isEqualTo(200);
        // 파일 문항이 없으면 비로그인 폼도 그대로 된다(기존 동작)
        assertThat(saveForm(admin, "ZZ첨부 일반문항", "N", "FIELD01").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("첨부한 파일은 응답에 매핑돼 고아 정리에 지워지지 않는다")
    void attachedFileSurvivesGc() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newFormWithFileField(admin, "ZZ첨부 GC");
        String fileId = upload(admin, "zzff-keep.png");

        String answerId = submit(admin, ids[0], ids[1], fileId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_ref WHERE map_key = ?::integer AND file_type = 'FORM'",
                Integer.class, answerId))
                .as("매핑이 없으면 제출한 서류가 다음 새벽에 사라진다").isEqualTo(1);
        // 값에는 파일 ID만 들어간다
        assertThat(jdbc.queryForObject(
                "SELECT value FROM form_answer_value WHERE form_answer_id = ?::integer", String.class, answerId))
                .isEqualTo(fileId);
    }

    @Test
    @DisplayName("남이 올린 파일은 내 응답에 붙일 수 없다")
    void cannotAttachSomeoneElsesFile() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");
        String[] ids = newFormWithFileField(admin, "ZZ첨부 소유검증");
        // ★ 제출자를 관리자로 두는 이유: 이 폼은 GEN 메뉴에 안 걸려 있어 일반 회원은 폼 접근에서
        //   먼저 403으로 막힌다. 그러면 소유 검증까지 가지도 못해 테스트가 무의미해진다.
        String othersFile = upload(user, "zzff-others.png");

        assertThat(post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + ids[0] + "\",\"values\":{\"" + ids[1] + "\":\"" + othersFile + "\"}}",
                admin).statusCode())
                .as("순차 ID를 찍어 남의 파일을 붙일 수 있으면 관리자 화면에서 그대로 유출된다")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("응답을 지우면 첨부도 비활성화돼 고아 정리 대상이 된다")
    void deletingAnswerReleasesFiles() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newFormWithFileField(admin, "ZZ첨부 삭제전파");
        String fileId = upload(admin, "zzff-drop.png");
        String answerId = submit(admin, ids[0], ids[1], fileId);

        assertThat(post("/api/adm/formanswer/deleteFormAnswer.do",
                "{\"rowId\":\"" + answerId + "\"}", admin).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject(
                "SELECT use_yn FROM file WHERE file_id = ?::integer", String.class, fileId))
                .as("전파를 빼먹으면 GC에 안 걸려 서류가 디스크에 영구히 남는다").isEqualTo("N");
    }

    @Test
    @DisplayName("응답 상세는 파일 ID가 아니라 파일명을 함께 준다")
    void answerViewIncludesFileMeta() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newFormWithFileField(admin, "ZZ첨부 상세");
        String fileId = upload(admin, "zzff-view.png");
        String answerId = submit(admin, ids[0], ids[1], fileId);

        String body = post("/api/adm/formanswer/selectFormAnswerView.do",
                "{\"rowId\":\"" + answerId + "\"}", admin).body();

        List<String> names = JsonPath.read(body, "$.data.files[*].originalName");
        assertThat(names).containsExactly("zzff-view.png");
    }

    @Test
    @DisplayName("파일첨부 문항은 집계에서 빠진다")
    void fileFieldExcludedFromStats() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newFormWithFileField(admin, "ZZ첨부 집계");
        String fileId = upload(admin, "zzff-stat.png");
        submit(admin, ids[0], ids[1], fileId);

        String body = post("/api/adm/formanswer/selectFormAnswerListStats.do",
                "{\"formId\":\"" + ids[0] + "\"}", admin).body();

        // 파일 ID를 세어 봐야 의미가 없다
        List<String> fieldIds = JsonPath.read(body, "$.data[*].fieldId");
        assertThat(fieldIds).doesNotContain(ids[1]);
    }

    @Test
    @DisplayName("내려받기에서는 파일첨부 값이 ID가 아니라 파일명으로 나간다")
    void exportShowsFileNames() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newFormWithFileField(admin, "ZZ첨부 내보내기");
        String a = upload(admin, "zzff-export-a.png");
        String b = upload(admin, "zzff-export-b.png");
        String answerId = submit(admin, ids[0], ids[1], a + "\\n" + b);

        String body = post("/api/adm/formanswer/selectFormAnswerListExport.do",
                "{\"formId\":\"" + ids[0] + "\"}", admin).body();
        String exported = JsonPath.read(body, "$.data.list[0].values['" + ids[1] + "']");

        // ID 그대로면 엑셀에 "12 / 13"이 찍혀 무슨 서류인지 알 수 없다. 순서도 제출 순서 그대로여야 한다
        assertThat(exported).isEqualTo("zzff-export-a.png\nzzff-export-b.png");

        // ⚠ 바꾸는 건 내려받기뿐 — 상세 화면은 이 ID로 다운로드 링크를 만든다
        String detail = post("/api/adm/formanswer/selectFormAnswerView.do",
                "{\"rowId\":\"" + answerId + "\"}", admin).body();
        String stored = JsonPath.read(detail, "$.data.values['" + ids[1] + "']");
        assertThat(stored).isEqualTo(a + "\n" + b);
    }

    @Test
    @DisplayName("이름을 찾을 수 없는 첨부는 빈칸이 아니라 '파일 없음'으로 남는다")
    void exportMarksMissingFile() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String[] ids = newFormWithFileField(admin, "ZZ첨부 파일없음");
        String fileId = upload(admin, "zzff-gone.png");
        submit(admin, ids[0], ids[1], fileId);
        // 파일 행이 사라진 상황(수동 정리 등) — 매핑과 답은 남아 있다
        jdbc.update("DELETE FROM file WHERE file_id = ?::integer", Integer.parseInt(fileId));

        String body = post("/api/adm/formanswer/selectFormAnswerListExport.do",
                "{\"formId\":\"" + ids[0] + "\"}", admin).body();
        String exported = JsonPath.read(body, "$.data.list[0].values['" + ids[1] + "']");

        // 빈칸이면 처음부터 첨부가 없었던 것처럼 보인다
        assertThat(exported).isEqualTo("(파일 없음 #" + fileId + ")");
    }

    // ===== helpers =====

    /** 파일첨부 문항 하나짜리 폼 생성 → [formId, fieldId] */
    private String[] newFormWithFileField(String token, String title) throws Exception {
        assertThat(saveForm(token, title, "Y", "FIELD09").statusCode()).isEqualTo(200);
        String formId = jdbc.queryForObject(
                "SELECT form_id::text FROM form WHERE title = ?", String.class, title);
        String fieldId = jdbc.queryForObject(
                "SELECT form_field_id::text FROM form_field WHERE form_id = ?::integer AND use_yn = 'Y'",
                String.class, formId);
        return new String[] {formId, fieldId};
    }

    private HttpResponse<String> saveForm(String token, String title, String loginYn, String fieldCd)
            throws Exception {
        String options = "FIELD03".equals(fieldCd) ? ",\"options\":\"가\\n나\"" : "";
        return post("/api/adm/form/insertForm.do",
                "{\"title\":\"" + title + "\",\"typeCd\":\"FORM01\",\"loginYn\":\"" + loginYn + "\","
                        + "\"multiYn\":\"Y\",\"fields\":[{\"label\":\"서류\",\"fieldCd\":\"" + fieldCd + "\","
                        + "\"requiredYn\":\"N\"" + options + "}]}", token);
    }

    private String submit(String token, String formId, String fieldId, String fileId) throws Exception {
        HttpResponse<String> res = post("/api/adm/formanswer/insertFormAnswer.do",
                "{\"formId\":\"" + formId + "\",\"values\":{\"" + fieldId + "\":\"" + fileId + "\"}}", token);
        assertThat(res.statusCode()).isEqualTo(200);
        String answerId = JsonPath.read(res.body(), "$.data");
        return answerId;
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
