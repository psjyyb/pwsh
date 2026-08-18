package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 모임 단체 대화 신고(CHAT) 검증.
 *
 * <p>대화는 사적이라 관리자도 들여다볼 수 없게 막아 두었다. 그래서 문제가 생기면 <b>신고</b>가 유일한 통로다.
 * - 멤버가 남의 말을 신고할 수 있고, 관리자 목록에 대화 내용 미리보기와 모집 링크가 붙는지
 * - '삭제조치'가 그 말 한 줄만 숨기는지(대화방 전체가 아니라)
 * - 없는 대화 신고는 막히는지
 */
class ChatReportTest extends IntegrationTest {

    @Test
    void chat_report_and_resolve() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"신고대화취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);

        assertEquals(200, signup("crhost", "신고대화주최", "crhost@test.local").statusCode());
        assertEquals(200, signup("crmem", "신고대화멤버", "crmem@test.local").statusCode());
        String host = accessToken("crhost", "Test1234!@");
        String mem = accessToken("crmem", "Test1234!@");

        String future = LocalDate.now().plusDays(6).toString();
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"신고대화 모집\",\"meetDt\":\"" + future + "\"}",
                host).body(), "$.data");
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", mem).statusCode());
        String applyId = jdbc.queryForObject(
                "SELECT apply_id::text FROM t_recruit_apply WHERE recruit_id = ?::integer AND user_id = 'crmem'",
                String.class, rid);
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + applyId + "\",\"applyStatus\":\"APPLY02\"}", host).statusCode());

        // 주최자가 두 마디 남긴다 — 그중 하나만 신고 대상
        assertEquals(200, post("/api/adm/recruitChat/insertRecruitChat.do",
                "{\"recruitId\":\"" + rid + "\",\"content\":\"문제되는 말\"}", host).statusCode());
        assertEquals(200, post("/api/adm/recruitChat/insertRecruitChat.do",
                "{\"recruitId\":\"" + rid + "\",\"content\":\"평범한 말\"}", host).statusCode());
        String badChat = jdbc.queryForObject(
                "SELECT chat_id::text FROM t_recruit_chat WHERE recruit_id = ?::integer AND content = '문제되는 말'",
                String.class, rid);

        // 멤버가 신고
        assertEquals(200, post("/api/adm/report/insertReport.do",
                "{\"targetType\":\"CHAT\",\"targetId\":\"" + badChat + "\",\"reason\":\"불쾌한 표현\"}",
                mem).statusCode());
        // 같은 대상 중복 신고는 차단
        assertNotEquals(200, post("/api/adm/report/insertReport.do",
                "{\"targetType\":\"CHAT\",\"targetId\":\"" + badChat + "\",\"reason\":\"또 신고\"}",
                mem).statusCode(), "중복 신고 차단");
        // 없는 대화는 신고 불가
        assertNotEquals(200, post("/api/adm/report/insertReport.do",
                "{\"targetType\":\"CHAT\",\"targetId\":\"99999999\",\"reason\":\"x\"}", mem).statusCode(),
                "없는 대화 신고 차단");

        // 관리자 목록: 대화 내용 미리보기 + 모집 링크
        String listBody = post("/api/adm/report/selectReportList.do",
                "{\"pageNo\":1,\"pageSize\":50,\"status\":\"PENDING\"}", admin).body();
        List<Object> titles = JsonPath.read(listBody,
                "$.data.list[?(@.targetType=='CHAT' && @.targetId=='" + badChat + "')].targetTitle");
        List<Object> links = JsonPath.read(listBody,
                "$.data.list[?(@.targetType=='CHAT' && @.targetId=='" + badChat + "')].linkUrl");
        assertEquals(1, titles.size(), "신고가 관리자 목록에 보인다");
        assertEquals("문제되는 말", titles.get(0), "대화 내용 미리보기");
        assertEquals("/gen/recruit/" + rid, links.get(0), "모집 상세로 연결");

        // 삭제조치 → 그 말만 숨김, 나머지 대화는 유지
        // 필터 결과는 배열이라 첫 원소를 꺼내야 한다(배열째 넘기면 ::integer 캐스트가 터진다)
        List<Object> reportIds = JsonPath.read(listBody,
                "$.data.list[?(@.targetType=='CHAT' && @.targetId=='" + badChat + "')].rowId");
        String reportId = String.valueOf(reportIds.get(0));
        assertEquals(200, post("/api/adm/report/updateReportStatus.do",
                "{\"rowId\":\"" + reportId + "\",\"status\":\"RESOLVED\"}", admin).statusCode());
        assertEquals("N", chatUseYn(badChat), "신고된 말은 숨김");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_recruit_chat WHERE recruit_id = ?::integer AND use_yn = 'Y'",
                Integer.class, rid), "나머지 대화는 그대로");

        // 되돌리기(PENDING) → 복원
        assertEquals(200, post("/api/adm/report/updateReportStatus.do",
                "{\"rowId\":\"" + reportId + "\",\"status\":\"PENDING\"}", admin).statusCode());
        assertEquals("Y", chatUseYn(badChat), "되돌리면 복원");
    }

    private String chatUseYn(String chatId) {
        return jdbc.queryForObject("SELECT use_yn FROM t_recruit_chat WHERE chat_id = ?::integer",
                String.class, chatId);
    }
}
