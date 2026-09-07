package com.pwsh.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.domain.config.service.ConfigService;
import com.pwsh.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 점검(유지보수) 모드.
 * - 켜면 일반 회원·비로그인은 503 + 안내 문구
 * - 관리자는 그대로 통과(점검을 끄러 들어올 수 있어야 한다)
 * - 로그인·환경설정 조회는 점검 중에도 열려 있다
 * - ★ 셀프 회원가입은 점검 중에 막힌다 — /api/auth/** 를 통째로 열지 않는 이유
 * - 끄면 즉시 복구
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class MaintenanceTest extends IntegrationTest {

    private static final String MSG = "점검 테스트 안내 문구";

    @Autowired
    private ConfigService configService;

    @AfterEach
    void restore() {
        jdbc.update("UPDATE config SET maint_yn = 'N'");
        configService.evictMaint();
    }

    /** 점검 모드를 켠다 — 관리자 화면과 같은 경로(환경설정 저장)로. */
    private void turnOn(String adminToken) throws Exception {
        String body = post("/api/adm/config/selectConfigView.do", "{}", adminToken).body();
        String req = "{\"failCntLimit\":\"" + (String) JsonPath.read(body, "$.data.failCntLimit") + "\""
                + ",\"failLockMins\":\"" + (String) JsonPath.read(body, "$.data.failLockMins") + "\""
                + ",\"passwordExpireDays\":\"" + (String) JsonPath.read(body, "$.data.passwordExpireDays") + "\""
                + ",\"sessionExpireMins\":\"" + (String) JsonPath.read(body, "$.data.sessionExpireMins") + "\""
                + ",\"delLogDays\":\"" + (String) JsonPath.read(body, "$.data.delLogDays") + "\""
                + ",\"title\":\"" + (String) JsonPath.read(body, "$.data.title") + "\""
                + ",\"accIpYn\":\"N\",\"maintYn\":\"Y\",\"maintMessage\":\"" + MSG + "\"}";
        assertEquals(200, post("/api/adm/config/updateConfig.do", req, adminToken).statusCode());
    }

    @Test
    void maintenance_blocks_members_but_not_admin() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String user = accessToken("user", "user1234!");

        // 켜기 전에는 회원도 정상
        assertEquals(200, post("/api/adm/post/selectPostList.do", "{\"boardId\":\"1\"}", user).statusCode());

        turnOn(admin);

        // 일반 회원 차단 — 상태 503 + 관리자가 입력한 안내 문구가 그대로 내려온다
        var blocked = post("/api/adm/post/selectPostList.do", "{\"boardId\":\"1\"}", user);
        assertEquals(503, blocked.statusCode());
        assertEquals("C503", (String) JsonPath.read(blocked.body(), "$.error.code"));
        assertEquals(MSG, (String) JsonPath.read(blocked.body(), "$.error.message"));

        // 비로그인도 차단
        assertEquals(503, post("/api/adm/post/selectPostList.do", "{\"boardId\":\"1\"}", null).statusCode());

        // 관리자는 통과 — 점검을 끄러 들어올 수 있어야 한다
        assertEquals(200, post("/api/adm/config/selectConfigView.do", "{}", admin).statusCode());
        assertEquals(200, post("/api/adm/member/selectMemberList.do", "{}", admin).statusCode());

        // 로그인은 점검 중에도 열려 있다(관리자가 새 기기에서 들어와야 할 수 있다)
        String reAdmin = accessToken("admin", "admin1234!");
        assertTrue(reAdmin != null && !reAdmin.isEmpty());
        // 회원도 로그인 자체는 되지만, 이후 요청은 여전히 막힌다
        String reUser = accessToken("user", "user1234!");
        assertEquals(503, post("/api/adm/post/selectPostList.do", "{\"boardId\":\"1\"}", reUser).statusCode());

        // 환경설정 조회는 비로그인에게도 열려 있다 — 점검 화면이 사이트명을 읽는다
        assertEquals(200, post("/api/adm/config/selectConfigView.do", "{}", null).statusCode());

        // ★ 셀프 회원가입은 막힌다 — 점검 중에 가입이 진행되면 안 된다
        assertEquals(503, signup("maintuser", "점검가입", "maint@test.com").statusCode());

        // 끄면 즉시 복구(캐시가 남아 몇 초간 막히면 안 된다)
        jdbc.update("UPDATE config SET maint_yn = 'N'");
        configService.evictMaint();
        assertEquals(200, post("/api/adm/post/selectPostList.do", "{\"boardId\":\"1\"}", reUser).statusCode());
    }
}
