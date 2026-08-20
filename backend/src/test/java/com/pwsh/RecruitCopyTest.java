package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 정기 모임(다음 회차 복제) 검증.
 * - 주최자만 복제할 수 있고, 원본 값이 승계되며 일정만 바뀌는지
 * - 참여자·대화·조회수가 넘어오지 않고 상태는 항상 모집중인지
 * - 이전 회차 확정 참여자에게 알림이 1건씩만 가는지(취미 팔로워 알림과 중복 없이)
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class RecruitCopyTest extends IntegrationTest {

    @Test
    void copy_creates_next_round_and_notifies_prev_members() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"정기취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);

        assertEquals(200, signup("cphost", "정기주최", "cphost@test.local").statusCode());
        assertEquals(200, signup("cpmem", "정기멤버", "cpmem@test.local").statusCode());
        assertEquals(200, signup("cpother", "남", "cpother@test.local").statusCode());
        String host = accessToken("cphost", "Test1234!@");
        String mem = accessToken("cpmem", "Test1234!@");
        String other = accessToken("cpother", "Test1234!@");

        // 멤버는 그 취미를 담아 둔다 — 이전 회차 알림과 취미 팔로워 알림이 겹치는 상황을 만든다
        assertEquals(200, post("/api/adm/memberHobby/insertMemberHobby.do",
                "{\"hobbyId\":\"" + hobbyId + "\"}", mem).statusCode());

        String d1 = LocalDate.now().plusDays(3).toString();
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"주말 정기산행\",\"content\":\"매주 토요일\","
                        + "\"capacity\":\"4\",\"areaCd\":\"AREA01\",\"region\":\"북한산\",\"meetDt\":\"" + d1 + "\"}",
                host).body(), "$.data");

        // 1회차 참여 확정
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", mem).statusCode());
        String applyId = jdbc.queryForObject(
                "SELECT apply_id::text FROM recruit_apply WHERE recruit_id = ?::integer AND member_id = 'cpmem'",
                String.class, rid);
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + applyId + "\",\"applyCd\":\"APPLY02\"}", host).statusCode());
        // 1회차 대화도 남겨 둔다(복제되지 않아야 한다)
        assertEquals(200, post("/api/adm/recruitChat/insertRecruitChat.do",
                "{\"recruitId\":\"" + rid + "\",\"content\":\"1회차 잘 다녀왔습니다\"}", host).statusCode());

        // 남이 남의 모집을 복제할 수 없다
        String d2 = LocalDate.now().plusDays(10).toString();
        assertNotEquals(200, post("/api/adm/recruit/insertRecruitCopy.do",
                "{\"rowId\":\"" + rid + "\",\"meetDt\":\"" + d2 + "\"}", other).statusCode(), "비주최자 복제 차단");
        assertNotEquals(200, post("/api/adm/recruit/insertRecruitCopy.do",
                "{\"rowId\":\"" + rid + "\",\"meetDt\":\"" + d2 + "\"}", null).statusCode(), "비로그인 복제 차단");
        // 일정 없이·지난 날짜로는 만들 수 없다
        assertNotEquals(200, post("/api/adm/recruit/insertRecruitCopy.do",
                "{\"rowId\":\"" + rid + "\"}", host).statusCode(), "일정 필수");
        assertNotEquals(200, post("/api/adm/recruit/insertRecruitCopy.do",
                "{\"rowId\":\"" + rid + "\",\"meetDt\":\"" + LocalDate.now().minusDays(1) + "\"}", host).statusCode(),
                "지난 날짜 차단");

        int notiBefore = notificationCnt("cpmem");

        // 복제(일정만 변경) — 새 모집 ID 반환
        String nid = JsonPath.read(post("/api/adm/recruit/insertRecruitCopy.do",
                "{\"rowId\":\"" + rid + "\",\"meetDt\":\"" + d2 + "\"}", host).body(), "$.data");
        assertNotNull(nid);
        assertNotEquals(rid, nid, "새 모집이 만들어진다");

        String body = post("/api/adm/recruit/selectRecruitView.do", "{\"rowId\":\"" + nid + "\"}", host).body();
        assertEquals("주말 정기산행", JsonPath.read(body, "$.data.title"), "제목 승계");
        assertEquals("매주 토요일", JsonPath.read(body, "$.data.content"), "설명 승계");
        assertEquals("4", String.valueOf(JsonPath.<Object>read(body, "$.data.capacity")), "정원 승계");
        assertEquals("북한산", JsonPath.read(body, "$.data.region"), "지역 승계");
        assertEquals(hobbyId, String.valueOf(JsonPath.<Object>read(body, "$.data.hobbyId")), "취미 승계");
        assertEquals(d2, JsonPath.read(body, "$.data.meetDt"), "일정은 새 값");
        assertEquals("RECRUIT01", JsonPath.read(body, "$.data.statusCd"), "복제본은 항상 모집중");
        assertEquals("0", String.valueOf(JsonPath.<Object>read(body, "$.data.applyCnt")), "신청자는 복제되지 않는다");
        assertEquals("Y", JsonPath.read(body, "$.data.mineYn"), "주최자는 그대로");

        // 대화는 복제되지 않는다(새 회차는 빈 대화방)
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM recruit_chat WHERE recruit_id = ?::integer", Integer.class, nid),
                "대화는 복제되지 않는다");

        // 이전 회차 참여자에게 알림 1건(취미 팔로워 알림과 중복 발송되지 않는다)
        assertEquals(notiBefore + 1, notificationCnt("cpmem"), "이전 회차 참여자 알림 1건");
        String link = jdbc.queryForObject(
                "SELECT link_url FROM notification WHERE member_id = 'cpmem' ORDER BY notification_id DESC LIMIT 1",
                String.class);
        assertEquals("/gen/recruit/" + nid, link, "알림은 새 회차로 연결");

        // 새 회차에는 이전 참여자도 새로 신청해야 한다(자동 참여가 아니다)
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + nid + "\"}", mem).statusCode());

        // 원본은 그대로 남는다(복제는 원본을 건드리지 않는다)
        String srcBody = post("/api/adm/recruit/selectRecruitView.do", "{\"rowId\":\"" + rid + "\"}", host).body();
        assertEquals(d1, JsonPath.read(srcBody, "$.data.meetDt"), "원본 일정 유지");
        assertEquals("1", String.valueOf(JsonPath.<Object>read(srcBody, "$.data.acceptedCnt")), "원본 참여자 유지");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM recruit_chat WHERE recruit_id = ?::integer AND use_yn = 'Y'",
                Integer.class, rid), "원본 대화 유지");
    }

    private int notificationCnt(String memberId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE member_id = ? AND use_yn = 'Y'", Integer.class, memberId);
        return n == null ? 0 : n;
    }
}
