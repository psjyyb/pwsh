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
 * 정원 자동 마감 + 대기 명단 검증.
 * - 정원 충족 시 자동 마감(기존 동작)이 유지되는지
 * - 정원으로 닫힌 모집은 '대기 신청'을 받고, 주최자 수동 마감 건은 받지 않는지
 * - 대기 순번(waitNo)이 신청순으로 매겨지고 수락 시 재계산되는지
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class RecruitWaitlistTest extends IntegrationTest {

    @Test
    void capacity_autoclose_and_waitlist_flow() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"대기취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);

        assertEquals(200, signup("wlhost", "대기주최", "wlhost@test.local").statusCode());
        assertEquals(200, signup("wl1", "신청1", "wl1@test.local").statusCode());
        assertEquals(200, signup("wl2", "신청2", "wl2@test.local").statusCode());
        assertEquals(200, signup("wl3", "신청3", "wl3@test.local").statusCode());
        String host = accessToken("wlhost", "Test1234!@");
        String t1 = accessToken("wl1", "Test1234!@");
        String t2 = accessToken("wl2", "Test1234!@");
        String t3 = accessToken("wl3", "Test1234!@");

        String future = LocalDate.now().plusDays(10).toString();
        // 정원 1명 모집
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"정원1모집\",\"capacity\":\"1\","
                        + "\"meetDt\":\"" + future + "\"}", host).body(), "$.data");

        // wl1 신청 → 수락 → 정원 충족으로 자동 마감
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", t1).statusCode());
        String a1 = applyId(rid, "wl1");
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + a1 + "\",\"applyCd\":\"APPLY02\"}", host).statusCode());
        assertEquals("RECRUIT02", status(rid), "정원 충족 시 자동 마감");

        // 정원으로 닫힌 모집 → 대기 신청 허용
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\",\"applyMemo\":\"대기할게요\"}", t2).statusCode(),
                "정원 마감 건은 대기 신청 허용");
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", t3).statusCode());

        // 대기 순번: 신청순 1, 2 (수락된 wl1은 null)
        String listBody = post("/api/adm/recruitApply/selectRecruitApplyList.do",
                "{\"recruitId\":\"" + rid + "\"}", host).body();
        assertEquals("1", waitNoOf(listBody, "wl2"));
        assertEquals("2", waitNoOf(listBody, "wl3"));
        List<Object> accepted = JsonPath.read(listBody, "$.data[?(@.memberId=='wl1')].waitNo");
        assertEquals(0, accepted.size(), "수락 건은 대기 순번이 없다");

        // 정원 초과 수락은 차단(자리가 없을 때)
        String a2 = applyId(rid, "wl2");
        assertNotEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + a2 + "\",\"applyCd\":\"APPLY02\"}", host).statusCode(),
                "정원이 찬 상태에선 수락 불가");

        // 자리가 나면(수락자 취소) 대기자를 수락할 수 있다 — 자동 승격은 하지 않는다
        assertEquals(200, post("/api/adm/recruitApply/deleteRecruitApply.do",
                "{\"rowId\":\"" + a1 + "\"}", t1).statusCode());
        assertEquals("RECRUIT02", status(rid), "자리가 나도 자동 재개하지 않는다(주최자 판단)");
        assertEquals("APPLY01", applyCd(a2), "자동 승격되지 않는다");
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + a2 + "\",\"applyCd\":\"APPLY02\"}", host).statusCode(),
                "주최자가 대기자를 수락");
        assertEquals("APPLY02", applyCd(a2));

        // wl2 수락 후 남은 대기자(wl3)의 순번은 1로 재계산
        String listBody2 = post("/api/adm/recruitApply/selectRecruitApplyList.do",
                "{\"recruitId\":\"" + rid + "\"}", host).body();
        assertEquals("1", waitNoOf(listBody2, "wl3"));

        // 주최자 수동 마감(정원 미달) 모집은 대기 신청도 받지 않는다
        String rid2 = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"수동마감\",\"capacity\":\"5\","
                        + "\"meetDt\":\"" + future + "\"}", host).body(), "$.data");
        assertEquals(200, post("/api/adm/recruit/updateRecruitStatus.do",
                "{\"rowId\":\"" + rid2 + "\",\"statusCd\":\"RECRUIT02\"}", host).statusCode());
        assertNotEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid2 + "\"}", t1).statusCode(),
                "수동 마감 건은 신청 차단");

        // 정원 무제한(0)은 자동 마감되지 않는다
        String rid3 = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"무제한\",\"capacity\":\"0\","
                        + "\"meetDt\":\"" + future + "\"}", host).body(), "$.data");
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid3 + "\"}", t1).statusCode());
        String a3 = applyId(rid3, "wl1");
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + a3 + "\",\"applyCd\":\"APPLY02\"}", host).statusCode());
        assertEquals("RECRUIT01", status(rid3), "정원 미설정은 자동 마감 없음");

        // 이미 지난 모임에는 신청할 수 없다
        String past = LocalDate.now().minusDays(1).toString();
        String rid4 = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"지난모임\",\"capacity\":\"5\","
                        + "\"meetDt\":\"" + past + "\"}", host).body(), "$.data");
        assertNotEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid4 + "\"}", t2).statusCode(), "지난 모임 신청 차단");
    }

    private String applyId(String recruitId, String memberId) {
        return jdbc.queryForObject(
                "SELECT apply_id::text FROM recruit_apply WHERE recruit_id = ?::integer AND member_id = ? AND use_yn = 'Y'",
                String.class, recruitId, memberId);
    }

    private String applyCd(String applyId) {
        return jdbc.queryForObject("SELECT apply_cd FROM recruit_apply WHERE apply_id = ?::integer",
                String.class, applyId);
    }

    private String status(String recruitId) {
        return jdbc.queryForObject("SELECT status_cd FROM recruit WHERE recruit_id = ?::integer",
                String.class, recruitId);
    }

    /** 신청자 목록 JSON에서 특정 회원의 대기 순번 추출. */
    private String waitNoOf(String body, String memberId) {
        List<Object> v = JsonPath.read(body, "$.data[?(@.memberId=='" + memberId + "')].waitNo");
        return v.isEmpty() ? null : String.valueOf(v.get(0));
    }
}
