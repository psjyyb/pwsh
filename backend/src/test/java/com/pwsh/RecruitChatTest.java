package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 모임 단체 대화(t_recruit_chat) 권한·동작 검증.
 * - 주최자와 수락된 참여자만 읽고 쓸 수 있는지(대기·거절·비참여·관리자는 차단)
 * - 수락이 취소되면 즉시 권한이 사라지는지(멤버 테이블 없이 조인으로 판정)
 * - 응답에 로그인 ID가 없고 mineYn/hostYn이 서버 계산으로 내려오는지
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class RecruitChatTest extends IntegrationTest {

    @Test
    void only_confirmed_members_can_read_and_write() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"대화취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);

        assertEquals(200, signup("rchost", "대화주최", "rchost@test.local").statusCode());
        assertEquals(200, signup("rcok", "확정멤버", "rcok@test.local").statusCode());
        assertEquals(200, signup("rcwait", "대기멤버", "rcwait@test.local").statusCode());
        assertEquals(200, signup("rcout", "비참여", "rcout@test.local").statusCode());
        String host = accessToken("rchost", "Test1234!@");
        String ok = accessToken("rcok", "Test1234!@");
        String wait = accessToken("rcwait", "Test1234!@");
        String out = accessToken("rcout", "Test1234!@");

        String future = LocalDate.now().plusDays(10).toString();
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"대화모집\",\"capacity\":\"0\","
                        + "\"meetDt\":\"" + future + "\"}", host).body(), "$.data");

        // 신청 2건 — 하나만 수락
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", ok).statusCode());
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", wait).statusCode());
        String okApply = applyId(rid, "rcok");
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + okApply + "\",\"applyStatus\":\"APPLY02\"}", host).statusCode());

        // 주최자·확정 멤버는 작성 가능
        assertEquals(200, post("/api/adm/recruitChat/insertRecruitChat.do",
                "{\"recruitId\":\"" + rid + "\",\"content\":\"토요일 9시 정문에서 만나요\"}", host).statusCode());
        assertEquals(200, post("/api/adm/recruitChat/insertRecruitChat.do",
                "{\"recruitId\":\"" + rid + "\",\"content\":\"네 늦지 않게 갈게요\"}", ok).statusCode());

        // 대기·비참여·비로그인은 읽기/쓰기 모두 차단
        assertNotEquals(200, post("/api/adm/recruitChat/selectRecruitChatList.do",
                "{\"recruitId\":\"" + rid + "\"}", wait).statusCode(), "대기 신청자는 읽기 차단");
        assertNotEquals(200, post("/api/adm/recruitChat/insertRecruitChat.do",
                "{\"recruitId\":\"" + rid + "\",\"content\":\"끼워주세요\"}", wait).statusCode(),
                "대기 신청자는 쓰기 차단");
        assertNotEquals(200, post("/api/adm/recruitChat/selectRecruitChatList.do",
                "{\"recruitId\":\"" + rid + "\"}", out).statusCode(), "비참여자는 읽기 차단");
        assertNotEquals(200, post("/api/adm/recruitChat/selectRecruitChatList.do",
                "{\"recruitId\":\"" + rid + "\"}", null).statusCode(), "비로그인 차단");
        // 관리자도 멤버가 아니면 볼 수 없다(사적 대화)
        assertNotEquals(200, post("/api/adm/recruitChat/selectRecruitChatList.do",
                "{\"recruitId\":\"" + rid + "\"}", admin).statusCode(), "관리자도 멤버가 아니면 차단");

        // 목록 내용·표시 필드 검증
        String body = post("/api/adm/recruitChat/selectRecruitChatList.do",
                "{\"recruitId\":\"" + rid + "\"}", ok).body();
        List<Object> contents = JsonPath.read(body, "$.data[*].content");
        assertEquals(2, contents.size(), "등록한 2건이 시간순으로 조회");
        assertEquals("토요일 9시 정문에서 만나요", contents.get(0));
        assertEquals("Y", JsonPath.read(body, "$.data[0].hostYn"), "주최자 말은 hostYn=Y");
        assertEquals("N", JsonPath.read(body, "$.data[0].mineYn"), "남의 말은 mineYn=N");
        assertEquals("Y", JsonPath.read(body, "$.data[1].mineYn"), "내 말은 mineYn=Y");
        assertTrue(JsonPath.<List<Object>>read(body, "$.data[?(@.regId)]").isEmpty(),
                "응답에 로그인 ID가 없다(handle만)");
        assertEquals("확정멤버", JsonPath.read(body, "$.data[1].regNm"));

        // 삭제는 작성자 본인만
        String myChat = JsonPath.read(body, "$.data[1].rowId");
        assertNotEquals(200, post("/api/adm/recruitChat/deleteRecruitChat.do",
                "{\"rowId\":\"" + myChat + "\"}", host).statusCode(), "남의 말은 주최자도 못 지운다");
        assertEquals(200, post("/api/adm/recruitChat/deleteRecruitChat.do",
                "{\"rowId\":\"" + myChat + "\"}", ok).statusCode());
        assertEquals(1, JsonPath.<List<Object>>read(post("/api/adm/recruitChat/selectRecruitChatList.do",
                "{\"recruitId\":\"" + rid + "\"}", host).body(), "$.data[*].content").size(),
                "삭제 후 1건");

        // 빈 내용은 거절
        assertNotEquals(200, post("/api/adm/recruitChat/insertRecruitChat.do",
                "{\"recruitId\":\"" + rid + "\",\"content\":\"   \"}", host).statusCode(), "빈 내용 차단");

        // 수락 취소 → 다음 요청부터 바로 권한 상실(멤버 테이블 없이 조인 판정)
        assertEquals(200, post("/api/adm/recruitApply/deleteRecruitApply.do",
                "{\"rowId\":\"" + okApply + "\"}", ok).statusCode());
        assertNotEquals(200, post("/api/adm/recruitChat/selectRecruitChatList.do",
                "{\"recruitId\":\"" + rid + "\"}", ok).statusCode(), "수락 취소 시 즉시 차단");
        assertEquals(200, post("/api/adm/recruitChat/selectRecruitChatList.do",
                "{\"recruitId\":\"" + rid + "\"}", host).statusCode(), "주최자는 계속 접근 가능");
    }

    private String applyId(String recruitId, String userId) {
        return jdbc.queryForObject(
                "SELECT apply_id::text FROM t_recruit_apply WHERE recruit_id = ?::integer AND user_id = ? AND use_yn = 'Y'",
                String.class, recruitId, userId);
    }
}
