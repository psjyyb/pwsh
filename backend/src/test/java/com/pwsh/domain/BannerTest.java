package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배너(메인 히어로) — 등록/조회/순서/삭제와 <b>노출 규칙</b> 검증.
 *
 * <p>메인 배너는 비로그인 방문자가 처음 보는 화면이다. 여기서 조용히 깨지는 것은 두 가지다:
 * ① 공개 경로(permitAll·인터셉터 예외)를 빠뜨려 게스트가 401 → 메인이 통째로 안 보인다,
 * ② 노출기간이 지난 배너가 계속 나온다(기간 판정을 조회 시점에 하므로 매퍼 조건이 유일한 방어선).
 * 그래서 상태코드만이 아니라 <b>목록에 무엇이 들어있는지</b>를 확인한다.
 */
class BannerTest extends IntegrationTest {

    @Test
    @DisplayName("비로그인 게스트도 메인 배너 목록을 읽는다(관리 목록은 401)")
    void guestReadsMainListButNotAdminList() throws Exception {
        assertThat(post("/api/adm/banner/selectBannerListMain.do", "{}", null).statusCode()).isEqualTo(200);
        assertThat(post("/api/adm/banner/selectBannerList.do", "{}", null).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("노출기간이 지난 배너는 메인 목록에서 빠진다(기간 미설정은 노출)")
    void expiredBannerExcludedFromMainList() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        String live = insert(admin, "{\"title\":\"ZZ상시노출\"}");
        String past = insert(admin, "{\"title\":\"ZZ지난배너\",\"startDt\":\"2000-01-01\",\"endDt\":\"2000-12-31\"}");
        String future = insert(admin, "{\"title\":\"ZZ예정배너\",\"startDt\":\"2999-01-01\"}");

        List<String> ids = mainIds();
        assertThat(ids).contains(live);
        assertThat(ids).doesNotContain(past, future);

        delete(admin, live);
        delete(admin, past);
        delete(admin, future);
    }

    @Test
    @DisplayName("등록 시 순서 자동부여 → UP/DOWN 교환이 실제 sortNo에 반영된다")
    void insertAssignsSortAndSwapWorks() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        String a = insert(admin, "{\"title\":\"ZZ순서A\"}");
        String b = insert(admin, "{\"title\":\"ZZ순서B\"}");
        String sa = sortOf(a, admin);
        String sb = sortOf(b, admin);
        assertThat(Integer.parseInt(sb)).isGreaterThan(Integer.parseInt(sa)); // 나중에 만든 쪽이 뒤

        assertThat(post("/api/adm/banner/updateBannerSort.do",
                "{\"rowId\":\"" + b + "\",\"direction\":\"UP\"}", admin).statusCode()).isEqualTo(200);
        assertThat(sortOf(b, admin)).isEqualTo(sa);
        assertThat(sortOf(a, admin)).isEqualTo(sb);

        delete(admin, a);
        delete(admin, b);
    }

    @Test
    @DisplayName("수정하면 제목·버튼이 바뀌고, 삭제하면 목록에서 사라진다")
    void updateAndDelete() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String id = insert(admin, "{\"title\":\"ZZ수정전\"}");

        assertThat(post("/api/adm/banner/updateBanner.do",
                "{\"rowId\":\"" + id + "\",\"title\":\"ZZ수정후\",\"btn1Label\":\"바로가기\",\"btn1Url\":\"/gen\"}",
                admin).statusCode()).isEqualTo(200);

        String view = post("/api/adm/banner/selectBannerView.do", "{\"rowId\":\"" + id + "\"}", admin).body();
        assertThat(JsonPath.<String>read(view, "$.data.title")).isEqualTo("ZZ수정후");
        assertThat(JsonPath.<String>read(view, "$.data.btn1Url")).isEqualTo("/gen");

        delete(admin, id);
        assertThat(mainIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("제목이 비면 등록되지 않는다(필수 검증)")
    void titleIsRequired() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(post("/api/adm/banner/insertBanner.do", "{\"title\":\"\"}", admin).statusCode())
                .isNotEqualTo(200);
    }

    @Test
    @DisplayName("배너를 지우면 배경 이미지도 비활성된다 — 빠뜨리면 고아 파일이 GC에 안 걸려 영구 누수")
    void deletePropagatesToImageFile() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 업로드 경로(멀티파트)를 태우지 않고 메타만 만든다 — 검증 대상은 '삭제 전파'이지 업로드가 아니다
        Long fileId = jdbc.queryForObject(
                "INSERT INTO file (path, stored_name, original_name, size, ext, use_yn,"
                        + " reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                        + " VALUES ('test', 'zz-banner.png', 'zz-banner.png', '1', 'png', 'Y',"
                        + " 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1') RETURNING file_id",
                Long.class);

        String id = insert(admin, "{\"title\":\"ZZ이미지배너\",\"fileId\":\"" + fileId + "\"}");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_ref WHERE map_key = ? AND file_id = ? AND file_type = 'BANNER'",
                Integer.class, Integer.parseInt(id), fileId)).isEqualTo(1);

        delete(admin, id);
        assertThat(jdbc.queryForObject("SELECT use_yn FROM file WHERE file_id = ?", String.class, fileId))
                .isEqualTo("N");

        jdbc.update("DELETE FROM file_ref WHERE file_id = ?", fileId);
        jdbc.update("DELETE FROM file WHERE file_id = ?", fileId);
    }

    // ===== helpers =====

    /** 등록 후 생성된 banner_id 반환(제목으로 되찾는다 — insert 응답은 data가 없다). */
    private String insert(String token, String json) throws Exception {
        assertThat(post("/api/adm/banner/insertBanner.do", json, token).statusCode()).isEqualTo(200);
        String title = JsonPath.read(json, "$.title");
        return String.valueOf(jdbc.queryForObject(
                "SELECT MAX(banner_id) FROM banner WHERE title = ?", Integer.class, title));
    }

    private void delete(String token, String rowId) throws Exception {
        post("/api/adm/banner/deleteBanner.do", "{\"rowId\":\"" + rowId + "\"}", token);
    }

    private String sortOf(String rowId, String token) throws Exception {
        String body = post("/api/adm/banner/selectBannerView.do", "{\"rowId\":\"" + rowId + "\"}", token).body();
        return String.valueOf(JsonPath.<Object>read(body, "$.data.sortNo"));
    }

    private List<String> mainIds() throws Exception {
        String body = post("/api/adm/banner/selectBannerListMain.do", "{}", null).body();
        return JsonPath.read(body, "$.data[*].rowId");
    }
}
