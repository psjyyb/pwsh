package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시스템 상태 · 캐시 · 설정 스냅샷.
 *
 * <p>★ 여기서 조용히 틀리는 건 <b>캐시 무효화</b>다. {@code @CacheEvict}가 빠져도 에러가 안 나고,
 * "코드를 고쳤는데 화면에 반영이 안 되다가 서버를 재시작하면 고쳐지는" 형태로 나타나 원인을
 * 찾기 매우 어렵다. 그래서 "바꾸면 바로 보인다"를 고정한다.
 *
 * <p>노출 쪽도 본다 — 경로·DB 크기·마이그레이션 이력은 공격자에게 쓸모 있는 정보라
 * 관리자만 봐야 한다.
 */
class SystemStatusTest extends IntegrationTest {

    /** 기초데이터에 있는 부모 코드(계정상태) — 하위에 STATUS01~04가 있다 */
    private static final String PARENT = "STATUS00";
    private static final String TEST_CODE = "ZZSYS01";

    @AfterEach
    void cleanup() throws Exception {
        jdbc.update("DELETE FROM code WHERE code_id = ?", TEST_CODE);
        // 테스트가 남긴 캐시를 비워 다른 테스트가 옛 목록을 보지 않게 한다
        post("/api/adm/system/updateSystemCache.do", "{}", accessToken("admin", "admin1234!"));
    }

    @Test
    @DisplayName("공통코드를 추가하면 콤보 캐시가 비워져 바로 보인다")
    void addingCodeInvalidatesComboCache() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 먼저 한 번 읽어 캐시를 채운다 — 이 단계가 없으면 무효화가 빠져도 테스트가 통과한다
        int before = comboSize(admin);

        assertThat(post("/api/adm/code/insertCode.do",
                "{\"rowId\":\"" + TEST_CODE + "\",\"pCodeId\":\"" + PARENT + "\","
                        + "\"codeName\":\"ZZ상태\",\"sortNo\":\"90\"}", admin).statusCode()).isEqualTo(200);

        assertThat(comboSize(admin))
                .as("@CacheEvict가 빠지면 추가한 코드가 안 보이고, 재시작해야 고쳐지는 것처럼 된다")
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("공통코드를 삭제하면 콤보 캐시가 비워져 바로 사라진다")
    void deletingCodeInvalidatesComboCache() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        post("/api/adm/code/insertCode.do",
                "{\"rowId\":\"" + TEST_CODE + "\",\"pCodeId\":\"" + PARENT + "\","
                        + "\"codeName\":\"ZZ상태\",\"sortNo\":\"90\"}", admin);
        int before = comboSize(admin);

        assertThat(post("/api/adm/code/deleteCode.do",
                "{\"rowId\":\"" + TEST_CODE + "\"}", admin).statusCode()).isEqualTo(200);

        assertThat(comboSize(admin)).isEqualTo(before - 1);
    }

    @Test
    @DisplayName("공통코드 콤보는 화면이 쓰는 두 필드를 준다")
    void comboReturnsIdAndName() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String body = post("/api/adm/code/selectCodeListCombo.do",
                "{\"pCodeId\":\"" + PARENT + "\"}", admin).body();

        // 프론트 useCodes가 codeId=value, codeName=label로 쓴다. 하나라도 비면 드롭다운이 빈칸이 되는데,
        // useCodes가 실패를 조용히 삼켜(.catch → []) 화면엔 에러가 안 뜬다 — 그래서 여기서 고정한다.
        List<String> ids = JsonPath.read(body, "$.data[*].codeId");
        List<String> names = JsonPath.read(body, "$.data[*].codeName");
        assertThat(ids).isNotEmpty().doesNotContainNull();
        assertThat(names).hasSameSizeAs(ids).doesNotContainNull();
    }

    @Test
    @DisplayName("캐시 비우기는 실제로 캐시를 비운다")
    void evictCacheEmptiesIt() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        comboSize(admin); // 캐시 채우기
        assertThat(cacheSize(admin)).isPositive();

        assertThat(post("/api/adm/system/updateSystemCache.do", "{}", admin).statusCode()).isEqualTo(200);

        assertThat(cacheSize(admin)).as("비운 뒤에는 항목이 없어야 한다").isZero();
    }

    @Test
    @DisplayName("상태 조회에 버전·마이그레이션 이력·디스크가 담긴다")
    void statusHasVersionAndMigrations() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String body = post("/api/adm/system/selectSystemView.do", "{}", admin).body();

        assertThat((String) JsonPath.read(body, "$.data.app.version")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.data.app.uptime")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.data.disk.path")).isNotBlank();
        // 테스트 DB는 Flyway가 clean → migrate 하므로 이력이 반드시 있다
        List<String> versions = JsonPath.read(body, "$.data.db.migrations[*].version");
        assertThat(versions).isNotEmpty();
        // 배치 4종(파일 GC·메일 이력·회원 라이프사이클·모집 알림)이 보여야 한다.
        // SSE 연결 유지 핑은 운영 배치가 아니라 여기 없고, 연결 수는 realtime 항목으로 나온다.
        List<String> schedules = JsonPath.read(body, "$.data.schedules[*].name");
        assertThat(schedules).hasSize(4);
        assertThat((int) JsonPath.read(body, "$.data.realtime.connections")).isNotNegative();
    }

    @Test
    @DisplayName("설정 스냅샷에는 설정만 담고 운영 데이터는 담지 않는다")
    void backupHasConfigOnly() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String body = get("/api/adm/system/downloadBackup.do", admin).body();

        assertThat((List<?>) JsonPath.read(body, "$.codes")).isNotEmpty();
        assertThat((List<?>) JsonPath.read(body, "$.menus")).isNotEmpty();
        assertThat(body).contains("exportedAt");
        // ⚠ 게시글·회원은 담지 않는다 — 개인정보가 섞이고 용량이 커진다
        assertThat(body).doesNotContain("\"posts\"").doesNotContain("\"members\"");
    }

    @Test
    @DisplayName("일반회원은 시스템 상태·캐시·스냅샷을 쓸 수 없다")
    void memberBlocked() throws Exception {
        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/system/selectSystemView.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(post("/api/adm/system/updateSystemCache.do", "{}", user).statusCode()).isEqualTo(403);
        assertThat(get("/api/adm/system/downloadBackup.do", user).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("메뉴 권한을 줘도 관리자가 아니면 시스템 상태를 못 본다")
    void menuPermissionAloneIsNotEnough() throws Exception {
        // 평소엔 메뉴권한 인터셉터가 먼저 막는다. 운영자가 실수로 이 메뉴를 다른 그룹에 열어주면
        // 그 방어가 사라지므로, 컨트롤러의 requireAdmin()이 마지막 방어선이 된다.
        Integer menuId = jdbc.queryForObject(
                "SELECT menu_id FROM menu WHERE area = 'ADM' AND link_url = '/adm/system'", Integer.class);
        jdbc.update("INSERT INTO auth (menu_id, conn_id, type, menu_yn, search_yn, mod_yn, use_yn,"
                + " reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES (?, 'MEMBER', 'GRP', 'Y', 'Y', 'N', 'Y',"
                + " 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')", menuId);
        try {
            assertThat(post("/api/adm/system/selectSystemView.do", "{}",
                    accessToken("user", "user1234!")).statusCode())
                    .as("메뉴가 열려도 관리자가 아니면 막아야 한다").isEqualTo(403);
        } finally {
            jdbc.update("DELETE FROM auth WHERE menu_id = ? AND conn_id = 'MEMBER'", menuId);
        }
    }

    // ===== helpers =====

    /** 콤보 조회 결과 건수(캐시를 타는 경로) */
    private int comboSize(String token) throws Exception {
        String body = post("/api/adm/code/selectCodeListCombo.do",
                "{\"pCodeId\":\"" + PARENT + "\"}", token).body();
        assertThat(body).as("콤보 조회 응답").contains("\"data\"");
        List<?> list = JsonPath.read(body, "$.data");
        return list.size();
    }

    /** 시스템 상태가 보고하는 캐시 항목 수(첫 캐시) */
    private int cacheSize(String token) throws Exception {
        String body = post("/api/adm/system/selectSystemView.do", "{}", token).body();
        List<Integer> sizes = JsonPath.read(body, "$.data.caches[*].size");
        return sizes.isEmpty() ? 0 : sizes.get(0);
    }
}
