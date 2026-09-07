package com.pwsh.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.domain.accessip.service.AccessIpService;
import com.pwsh.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 관리자 접속 IP 제한.
 * - 목록 API가 현재 접속 IP·제한 동작 여부를 함께 내리는지
 * - 잘못된 IP 형식은 등록이 거부되는지, 중복 등록은 409인지
 * - 제한이 켜져 있고 내 IP가 목록에 없으면 관리자 API·로그인이 403인지
 * - 목록이 비면 제한이 동작하지 않는지(전원 잠금 방지)
 * - 자기 잠금 방지: 내 IP 없이 제한을 켜는 것, 내 IP를 지우는 것이 막히는지
 * - 일반 회원은 IP 제한 대상이 아닌지(사용자 사이트가 막히면 안 된다)
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class AccessIpTest extends IntegrationTest {

    /** 캐시(30초 TTL)를 쓰므로 jdbc로 직접 바꾼 상태는 evict로 즉시 반영시킨다. */
    @Autowired
    private AccessIpService accessIpService;

    @AfterEach
    void restore() {
        jdbc.update("DELETE FROM access_ip");
        jdbc.update("UPDATE config SET acc_ip_yn = 'N'");
        accessIpService.evict();
    }

    @Test
    void list_returns_my_ip_and_enforce_flag() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String body = post("/api/adm/accessip/selectAccessIpList.do", "{}", admin).body();
        assertNotNull(JsonPath.read(body, "$.data.myIp"), "현재 접속 IP를 내려줘야 화면에서 '내 IP 넣기'가 가능하다");
        assertEquals("N", JsonPath.read(body, "$.data.enforcedYn"), "기본 시드는 제한 꺼짐");
    }

    @Test
    void invalid_ip_format_is_rejected() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertEquals(400, post("/api/adm/accessip/insertAccessIp.do",
                "{\"ip\":\"office-pc\"}", admin).statusCode());
        assertEquals(400, post("/api/adm/accessip/insertAccessIp.do",
                "{\"ip\":\"192.168.0.0/33\"}", admin).statusCode());
        assertEquals(400, post("/api/adm/accessip/insertAccessIp.do",
                "{\"ip\":\"192.168.0.256\"}", admin).statusCode());
        // 정상 형식은 등록된다
        assertEquals(200, post("/api/adm/accessip/insertAccessIp.do",
                "{\"ip\":\"192.168.0.0/24\",\"description\":\"사무실\"}", admin).statusCode());
        // 같은 IP 중복 등록은 거부 — pwsh의 ErrorCode.DUPLICATE 는 409(CONFLICT)로 매핑된다
        assertEquals(409, post("/api/adm/accessip/insertAccessIp.do",
                "{\"ip\":\"192.168.0.0/24\",\"description\":\"사무실2\"}", admin).statusCode());
    }

    @Test
    void empty_list_does_not_enforce() throws Exception {
        // 제한을 켜도 허용 IP가 하나도 없으면 통과 — 아무도 못 들어가는 상태를 만들지 않는다
        jdbc.update("UPDATE config SET acc_ip_yn = 'Y'");
        accessIpService.evict();
        String admin = accessToken("admin", "admin1234!");
        assertEquals(200, post("/api/adm/accessip/selectAccessIpList.do", "{}", admin).statusCode());
    }

    @Test
    void admin_from_disallowed_ip_is_blocked() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 내 IP가 아닌 문서용 IP만 허용 + 제한 ON (API로 켜면 자기 잠금 방지 검사에 막히므로 직접 세팅)
        jdbc.update("INSERT INTO access_ip (ip, description, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) "
                + "VALUES ('203.0.113.1', '테스트', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1')");
        jdbc.update("UPDATE config SET acc_ip_yn = 'Y'");
        accessIpService.evict();

        // 이미 발급된 토큰으로도 관리자 API 차단
        assertEquals(403, post("/api/adm/accessip/selectAccessIpList.do", "{}", admin).statusCode());
        assertEquals(403, post("/api/adm/member/selectMemberList.do", "{}", admin).statusCode());
        // 로그인 자체도 차단 — 토큰을 아예 발급하지 않는다
        assertEquals(403, login("admin", "admin1234!").statusCode());

        // 일반 회원은 대상이 아니다 — 로그인·사용자 콘텐츠 API 모두 정상
        String user = accessToken("user", "user1234!");
        assertTrue(user != null && !user.isEmpty());
        assertEquals(200, post("/api/adm/post/selectPostList.do", "{\"boardId\":\"1\"}", user).statusCode());

        // 허용 IP를 지우면 제한이 풀린다(목록이 비면 미동작)
        jdbc.update("DELETE FROM access_ip");
        accessIpService.evict();
        assertEquals(200, post("/api/adm/accessip/selectAccessIpList.do", "{}", admin).statusCode());
    }

    @Test
    void self_lockout_is_prevented() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 내 IP가 아닌 것만 등록한 상태에서 환경설정으로 제한을 켜려 하면 거부
        assertEquals(200, post("/api/adm/accessip/insertAccessIp.do",
                "{\"ip\":\"203.0.113.1\",\"description\":\"테스트\"}", admin).statusCode());
        assertEquals(400, post("/api/adm/config/updateConfig.do", configBody(admin, "Y"), admin).statusCode());
        assertEquals("N", jdbc.queryForObject("SELECT acc_ip_yn FROM config", String.class),
                "거부되었으니 설정은 그대로여야 한다");

        // 내 IP를 추가하면 켤 수 있다
        String myIp = JsonPath.read(
                post("/api/adm/accessip/selectAccessIpList.do", "{}", admin).body(), "$.data.myIp");
        assertEquals(200, post("/api/adm/accessip/insertAccessIp.do",
                "{\"ip\":\"" + myIp + "\",\"description\":\"내 PC\"}", admin).statusCode());
        assertEquals(200, post("/api/adm/config/updateConfig.do", configBody(admin, "Y"), admin).statusCode());
        assertEquals("Y", JsonPath.read(
                post("/api/adm/accessip/selectAccessIpList.do", "{}", admin).body(), "$.data.enforcedYn"));

        // 제한이 걸린 상태에서 내 IP 항목을 지우는 것은 막힌다(다른 항목이 남아 있으므로 잠김)
        String myRowId = jdbc.queryForObject(
                "SELECT access_ip_id::text FROM access_ip WHERE ip = ? AND use_yn = 'Y'", String.class, myIp);
        assertEquals(400, post("/api/adm/accessip/deleteAccessIp.do",
                "{\"rowId\":\"" + myRowId + "\"}", admin).statusCode());
        // 내 IP가 아닌 항목은 지울 수 있다
        String otherRowId = jdbc.queryForObject(
                "SELECT access_ip_id::text FROM access_ip WHERE ip = '203.0.113.1' AND use_yn = 'Y'", String.class);
        assertEquals(200, post("/api/adm/accessip/deleteAccessIp.do",
                "{\"rowId\":\"" + otherRowId + "\"}", admin).statusCode());
        // 마지막 1건은 지워도 된다 — 목록이 비면 제한 자체가 풀리므로 잠기지 않는다
        assertEquals(200, post("/api/adm/accessip/deleteAccessIp.do",
                "{\"rowId\":\"" + myRowId + "\"}", admin).statusCode());
    }

    /** 현재 환경설정을 읽어 acc_ip_yn만 바꾼 전체 본문 — 부분 전송하면 다른 설정이 지워진다. */
    private String configBody(String adminToken, String accIpYn) throws Exception {
        String body = post("/api/adm/config/selectConfigView.do", "{}", adminToken).body();
        return "{\"failCntLimit\":\"" + (String) JsonPath.read(body, "$.data.failCntLimit") + "\""
                + ",\"failLockMins\":\"" + (String) JsonPath.read(body, "$.data.failLockMins") + "\""
                + ",\"passwordExpireDays\":\"" + (String) JsonPath.read(body, "$.data.passwordExpireDays") + "\""
                + ",\"sessionExpireMins\":\"" + (String) JsonPath.read(body, "$.data.sessionExpireMins") + "\""
                + ",\"delLogDays\":\"" + (String) JsonPath.read(body, "$.data.delLogDays") + "\""
                + ",\"title\":\"" + (String) JsonPath.read(body, "$.data.title") + "\""
                + ",\"accIpYn\":\"" + accIpYn + "\"}";
    }
}
