package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 약관 동의 검증 — 개인정보를 수집하므로 필수 동의 없이는 가입이 되어서는 안 된다.
 * - 필수 약관 미동의/일부만 동의 → 가입 거부(400), 계정이 생기지 않는지
 * - 동의하면 가입 + member_policy에 동의 이력(시각·IP)이 남는지
 * - 약관 공개 조회는 비로그인도 가능한지(가입 화면·푸터가 읽는다), 목록에 본문은 안 실리는지
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class PolicyAgreeTest extends IntegrationTest {

    @Test
    void signup_rejected_without_required_agreement() throws Exception {
        // 필수 약관을 하나도 동의하지 않음
        assertEquals(400, signup("pcnone", "약관미동의", "pcnone@test.local", "[]").statusCode());
        assertEquals(0, memberCnt("pcnone"), "가입이 거부되면 계정이 남지 않아야 한다");

        // 필수 약관이 2건인데 1건만 동의 → 여전히 거부
        List<String> required = jdbc.queryForList(
                "SELECT policy_id::text FROM policy WHERE use_yn = 'Y' AND req_yn = 'Y' ORDER BY policy_id",
                String.class);
        assertTrue(required.size() >= 2, "기초데이터에 필수 약관이 2건 이상이어야 이 검증이 의미가 있다");
        String partial = "[\"" + required.get(0) + "\"]";
        assertEquals(400, signup("pcpart", "일부동의", "pcpart@test.local", partial).statusCode());
        assertEquals(0, memberCnt("pcpart"));
    }

    @Test
    void signup_records_agreement_history() throws Exception {
        assertEquals(200, signup("pcok", "약관동의", "pcok@test.local").statusCode());

        List<String> required = jdbc.queryForList(
                "SELECT policy_id::text FROM policy WHERE use_yn = 'Y' AND req_yn = 'Y' ORDER BY policy_id",
                String.class);
        Integer agreed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_policy WHERE member_id = ? AND use_yn = 'Y'", Integer.class, "pcok");
        assertEquals(required.size(), agreed, "동의한 약관 수만큼 이력이 남아야 한다");

        // 동의 증빙: 시각·IP가 비어 있으면 안 된다
        Integer withProof = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_policy WHERE member_id = ? AND reg_dt IS NOT NULL AND reg_ip IS NOT NULL",
                Integer.class, "pcok");
        assertEquals(required.size(), withProof);
    }

    @Test
    void policy_public_list_and_view_are_open_to_guest() throws Exception {
        // 가입 화면·푸터는 비로그인 상태에서 약관을 읽어야 한다(막히면 화면이 깨진다)
        var listRes = post("/api/adm/policy/selectPolicyListPublic.do", "{}", null);
        assertEquals(200, listRes.statusCode());
        List<String> titles = JsonPath.read(listRes.body(), "$.data.list[*].title");
        assertTrue(titles.size() >= 2, "공개 약관이 2건 이상 내려와야 한다");
        assertTrue(!listRes.body().contains("\"content\""), "목록에 본문을 실어보내지 않는다");

        // 필수동의가 앞에 오도록 정렬(화면에서 필수 항목이 먼저 보인다)
        String firstReq = JsonPath.read(listRes.body(), "$.data.list[0].reqYn");
        assertEquals("Y", firstReq);

        // String.valueOf(JsonPath.read(...))로 쓰면 제네릭이 char[] 오버로드로 추론돼 ClassCastException이 난다
        String rowId = JsonPath.read(listRes.body(), "$.data.list[0].rowId");
        var viewRes = post("/api/adm/policy/selectPolicyView.do", "{\"rowId\":\"" + rowId + "\"}", null);
        assertEquals(200, viewRes.statusCode());
        String content = JsonPath.read(viewRes.body(), "$.data.content");
        assertNotNull(content);
        assertTrue(content.length() > 100, "본문이 조회돼야 한다");
    }

    private int memberCnt(String memberId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE member_id = ?", Integer.class, memberId);
        return n == null ? 0 : n;
    }
}
