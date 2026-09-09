package com.pwsh.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pwsh.common.message.Messages;
import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

/**
 * 메시지 소스 배선 검증.
 *
 * <p>확인하려는 것은 "properties 파일이 있다"가 아니라 <b>응답에 실제로 그 값이 나가는가</b>다.
 * 문구가 코드에 남아 있으면 properties를 고쳐도 응답이 안 바뀌므로, API 응답으로 대조한다.
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class MessageSourceTest extends IntegrationTest {

    @Test
    void business_exception_message_comes_from_properties() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // messages.properties의 error.member.duplicateId 값이 그대로 응답에 실린다
        assertEquals(200, createMember(admin, "msgdup").statusCode());
        var res = createMember(admin, "msgdup"); // 같은 ID 재등록 → 중복
        assertEquals(400, res.statusCode());
        assertEquals(Messages.get("error.member.duplicateId"),
                (String) JsonPath.read(res.body(), "$.error.message"));

        jdbc.update("DELETE FROM auth_member WHERE member_id = 'msgdup'");
        jdbc.update("DELETE FROM member WHERE member_id = 'msgdup'");
    }

    @Test
    void message_with_arguments_is_formatted() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // Validate.required → error.validate.required 에 라벨({0})이 채워진다
        var res = post("/api/adm/accessip/insertAccessIp.do", "{}", admin);
        assertEquals(400, res.statusCode());
        String message = JsonPath.read(res.body(), "$.error.message");
        assertEquals(Messages.get("error.validate.required", "IP"), message);
        assertTrue(message.contains("IP"), "인자가 치환되지 않으면 {0}이 그대로 남는다");
        assertTrue(!message.contains("{0}"), "치환되지 않은 자리표시자가 남아서는 안 된다");
    }

    @Test
    void missing_key_returns_the_key_itself() {
        // 조용히 빈 문자열이 나가면 원인 추적이 어려워, 키를 그대로 돌려주도록 했다
        String key = "error.nonexistent.key.for.test";
        assertEquals(key, Messages.get(key));
        // 선택적 조회는 null — ErrorCode 재정의처럼 "없는 게 정상"인 자리에서 쓴다
        org.junit.jupiter.api.Assertions.assertNull(Messages.find(key));
    }

    @Test
    void error_code_default_message_is_overridable() throws Exception {
        // ErrorCode 기본 메시지는 properties에 error.{코드}가 있으면 그 값이 나간다.
        // 지금은 재정의를 두지 않았으므로 enum의 기본값이 그대로 쓰이는지 확인한다
        // (재정의가 없을 때 폴백이 깨지면 응답 메시지가 키 문자열로 새어 나간다).
        var res = post("/api/adm/member/selectMemberList.do", "{}", null); // 비로그인 → 401
        assertEquals(401, res.statusCode());
        String message = JsonPath.read(res.body(), "$.error.message");
        assertTrue(!message.startsWith("error."), "폴백이 깨지면 키 문자열이 그대로 응답에 실린다: " + message);
    }
}
