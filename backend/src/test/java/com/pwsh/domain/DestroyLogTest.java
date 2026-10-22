package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.member.service.MemberService;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * 개인정보 파기 이력 — 누가·언제·어떤 경로로 파기했는지.
 *
 * <p>파기는 되돌릴 수 없어서 "언제 왜 지워졌나"를 나중에 증명할 수 있어야 한다. 조용히 틀릴 수 있는 곳 —
 * 자동 파기(배치)와 관리자 즉시 파기가 서로 다른 메서드라 한쪽에만 기록을 넣기 쉽고,
 * 파기가 실패(이미 파기됨)했는데 이력만 쌓이면 이력이 사실과 어긋난다.
 */
class DestroyLogTest extends IntegrationTest {

    private static final String MIGRATION = "db/migration/V7__login_log_destroy_log.sql";
    private static final String BACKFILL_MARK = "-- ▼ backfill";

    @Autowired
    private MemberService memberService;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM destroy_log WHERE member_id LIKE 'zzdl%'");
        jdbc.update("DELETE FROM event_log WHERE target_id LIKE 'zzdl%'");
        jdbc.update("DELETE FROM auth_member WHERE member_id LIKE 'zzdl%'");
        jdbc.update("DELETE FROM member WHERE member_id LIKE 'zzdl%'");
        jdbc.update("UPDATE config SET destroy_days = 30");
    }

    @Test
    @DisplayName("관리자가 즉시 파기하면 처리자와 함께 '관리자 즉시 파기'로 남는다")
    void destroyNowIsLoggedWithActor() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzdl1");

        assertThat(post("/api/adm/member/updateMemberDestroy.do", "{\"memberId\":\"zzdl1\"}", admin).statusCode())
                .isEqualTo(200);

        Map<String, Object> row = onlyRow("zzdl1");
        assertThat(row.get("destroy_cd")).isEqualTo("DESTROY02");
        assertThat(row.get("actor_id")).as("누가 지웠는지가 남아야 한다").isEqualTo("admin");
    }

    @Test
    @DisplayName("보존기간이 지나 자동 파기되면 탈퇴일과 함께 '자동 파기'로 남는다")
    void sweepIsLoggedAsAutomatic() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzdl2");
        assertThat(post("/api/adm/member/deleteMember.do", "{\"rowId\":\"zzdl2\"}", admin).statusCode())
                .isEqualTo(200);
        jdbc.update("UPDATE member SET withdraw_dt = NOW() - INTERVAL '60 days' WHERE member_id = 'zzdl2'");

        assertThat(memberService.sweepDestroy()).isPositive();

        Map<String, Object> row = onlyRow("zzdl2");
        assertThat(row.get("destroy_cd")).isEqualTo("DESTROY01");
        assertThat(row.get("actor_id")).as("배치는 사람이 아니다").isNull();
        assertThat(row.get("withdraw_dt")).as("언제 탈퇴해서 언제 지워졌는지 보존기간 계산 근거").isNotNull();
    }

    @Test
    @DisplayName("이미 파기된 계정을 다시 파기하면 거절되고 이력도 늘지 않는다")
    void failedDestroyIsNotLogged() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzdl3");
        post("/api/adm/member/updateMemberDestroy.do", "{\"memberId\":\"zzdl3\"}", admin);

        assertThat(post("/api/adm/member/updateMemberDestroy.do", "{\"memberId\":\"zzdl3\"}", admin).statusCode())
                .isEqualTo(400);

        assertThat(count("zzdl3")).as("실제로 지우지 않은 건 이력에 없어야 한다").isEqualTo(1);
    }

    @Test
    @DisplayName("관리자는 유형으로 걸러 볼 수 있고, 일반 회원은 볼 수 없다")
    void listIsAdminOnlyAndFilterable() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzdl4");
        post("/api/adm/member/updateMemberDestroy.do", "{\"memberId\":\"zzdl4\"}", admin);

        String body = post("/api/adm/destroylog/selectDestroyLogList.do",
                "{\"destroyCd\":\"DESTROY02\",\"filterField\":\"member_id\",\"filterKeyword\":\"zzdl4\"}", admin).body();
        List<String> ids = JsonPath.read(body, "$.data.list[*].memberId");
        assertThat(ids).containsExactly("zzdl4");
        assertThat((String) JsonPath.read(body, "$.data.list[0].destroyName")).isNotBlank();

        String none = post("/api/adm/destroylog/selectDestroyLogList.do",
                "{\"destroyCd\":\"DESTROY01\",\"filterField\":\"member_id\",\"filterKeyword\":\"zzdl4\"}", admin).body();
        assertThat((Integer) JsonPath.read(none, "$.data.totalCount")).isZero();

        String user = accessToken("user", "user1234!");
        assertThat(post("/api/adm/destroylog/selectDestroyLogList.do", "{}", user).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("이력 테이블이 생기기 전에 파기된 계정도 활동로그를 근거로 채운다(두 번 돌려도 한 줄)")
    void backfillFromEventLog() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzdl5");
        createMember(admin, "zzdl6");
        createMember(admin, "zzdl7");
        // 이력 테이블이 생기기 전 상태를 재현: 계정은 파기돼 있고 이력은 없다
        jdbc.update("UPDATE member SET destroy_dt = NOW(), use_yn = 'N' WHERE member_id IN ('zzdl5','zzdl6','zzdl7')");
        jdbc.update("INSERT INTO event_log (event_cd, member_id, target_table, target_id, reg_dt, reg_ip)"
                + " VALUES ('MEMBER_DESTROY', 'admin', 'member', 'zzdl5', NOW(), '127.0.0.1')");
        jdbc.update("INSERT INTO event_log (event_cd, member_id, target_table, target_id, reg_dt, reg_ip)"
                + " VALUES ('MEMBER_DESTROY', NULL, 'member', 'zzdl6', NOW(), '127.0.0.1')");

        String backfill = backfillSql();
        jdbc.execute(backfill);
        jdbc.execute(backfill);

        assertThat(onlyRow("zzdl5").get("destroy_cd")).isEqualTo("DESTROY02");
        assertThat(onlyRow("zzdl5").get("actor_id")).isEqualTo("admin");
        assertThat(onlyRow("zzdl6").get("destroy_cd")).isEqualTo("DESTROY01");
        // 근거가 없으면 추측하지 않는다
        assertThat(onlyRow("zzdl7").get("destroy_cd")).isEqualTo("DESTROY09");
    }

    // ===== helpers =====

    private Map<String, Object> onlyRow(String memberId) {
        assertThat(count(memberId)).isEqualTo(1);
        return jdbc.queryForMap("SELECT destroy_cd, actor_id, withdraw_dt FROM destroy_log WHERE member_id = ?",
                memberId);
    }

    private int count(String memberId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM destroy_log WHERE member_id = ?", Integer.class, memberId);
        return n == null ? 0 : n;
    }

    /** 마이그레이션의 채우기 구간만 꺼낸다 — 테스트에 SQL을 따로 적으면 원본과 어긋나도 모른다. */
    private String backfillSql() throws Exception {
        String sql = new String(new ClassPathResource(MIGRATION).getContentAsByteArray(), StandardCharsets.UTF_8);
        int i = sql.indexOf(BACKFILL_MARK);
        assertThat(i).as("마이그레이션에 채우기 구간 표시가 있어야 한다").isPositive();
        return sql.substring(i);
    }
}
