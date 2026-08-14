package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 후기 작성 자격 검증. 후기는 평점(신뢰지표)에 직결되므로 아무나 아무에게 쓸 수 없어야 한다.
 * - 같은 모임에서 실제로 함께한 사이(주최자 ↔ 수락된 참여자)만
 * - 모임이 끝난 뒤에만, 같은 대상에게 중복 불가, 자기 자신에게 불가
 */
class ReviewEligibilityTest extends IntegrationTest {

    @Test
    void only_participants_of_a_finished_meetup_can_review_each_other() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"후기취미\",\"summary\":\"s\"}", admin).body(), "$.data");

        assertEquals(200, signup("rvhost", "후기주최", "rvhost@test.local").statusCode());
        assertEquals(200, signup("rvjoin", "후기참여", "rvjoin@test.local").statusCode());
        assertEquals(200, signup("rvwait", "후기대기", "rvwait@test.local").statusCode());
        assertEquals(200, signup("rvout", "무관한사람", "rvout@test.local").statusCode());
        String host = accessToken("rvhost", "Test1234!@");
        String join = accessToken("rvjoin", "Test1234!@");
        String wait = accessToken("rvwait", "Test1234!@");
        String out = accessToken("rvout", "Test1234!@");
        String hostH = JsonPath.read(post("/api/auth/me", "{}", host).body(), "$.data.handle");
        String joinH = JsonPath.read(post("/api/auth/me", "{}", join).body(), "$.data.handle");

        String future = LocalDate.now().plusDays(5).toString();
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"후기 모임\",\"capacity\":\"5\",\"meetDt\":\"" + future + "\"}",
                host).body(), "$.data");

        // 참여자 1명 수락, 1명은 대기 상태로 남긴다
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", join).statusCode());
        assertEquals(200, post("/api/adm/recruitApply/insertRecruitApply.do",
                "{\"recruitId\":\"" + rid + "\"}", wait).statusCode());
        String joinApply = jdbc.queryForObject(
                "SELECT apply_id::text FROM t_recruit_apply WHERE recruit_id = ?::integer AND user_id = 'rvjoin'",
                String.class, rid);
        assertEquals(200, post("/api/adm/recruitApply/updateRecruitApply.do",
                "{\"rowId\":\"" + joinApply + "\",\"applyStatus\":\"APPLY02\"}", host).statusCode());

        // 1) 모임이 끝나기 전에는 쓸 수 없다
        assertNotEquals(200, post("/api/adm/review/insertReview.do",
                "{\"recruitId\":\"" + rid + "\",\"targetHandle\":\"" + hostH + "\",\"rating\":\"5\"}", join).statusCode(),
                "진행 중인 모임에 후기를 쓸 수 있으면 안 된다");

        // 모임 종료(마감)
        assertEquals(200, post("/api/adm/recruit/updateRecruitStatus.do",
                "{\"rowId\":\"" + rid + "\",\"statusCd\":\"RECRUIT02\"}", host).statusCode());

        // 2) 참여자 → 주최자 : 허용
        assertEquals(200, post("/api/adm/review/insertReview.do",
                "{\"recruitId\":\"" + rid + "\",\"targetHandle\":\"" + hostH + "\",\"rating\":\"5\",\"content\":\"좋았습니다\"}",
                join).statusCode());

        // 3) 주최자 → 수락 참여자 : 허용
        assertEquals(200, post("/api/adm/review/insertReview.do",
                "{\"recruitId\":\"" + rid + "\",\"targetHandle\":\"" + joinH + "\",\"rating\":\"4\",\"content\":\"함께 즐거웠어요\"}",
                host).statusCode());

        // 4) 같은 대상에게 중복 불가
        assertNotEquals(200, post("/api/adm/review/insertReview.do",
                "{\"recruitId\":\"" + rid + "\",\"targetHandle\":\"" + hostH + "\",\"rating\":\"3\"}", join).statusCode(),
                "중복 후기가 허용되면 평점을 조작할 수 있다");

        // 5) 자기 자신에게 불가
        assertNotEquals(200, post("/api/adm/review/insertReview.do",
                "{\"recruitId\":\"" + rid + "\",\"targetHandle\":\"" + hostH + "\",\"rating\":\"5\"}", host).statusCode(),
                "자기 자신에게 후기를 쓸 수 있으면 안 된다");

        // 6) 대기(APPLY01) 상태는 함께한 사이가 아니다
        assertNotEquals(200, post("/api/adm/review/insertReview.do",
                "{\"recruitId\":\"" + rid + "\",\"targetHandle\":\"" + hostH + "\",\"rating\":\"5\"}", wait).statusCode(),
                "수락되지 않은 신청자는 후기를 쓸 수 없다");

        // 7) 모임과 무관한 회원은 불가
        assertNotEquals(200, post("/api/adm/review/insertReview.do",
                "{\"recruitId\":\"" + rid + "\",\"targetHandle\":\"" + hostH + "\",\"rating\":\"1\"}", out).statusCode(),
                "참여하지 않은 회원이 평점을 남길 수 있으면 안 된다");

        // 평점 집계 — 주최자는 1건(5점)
        String stats = post("/api/adm/review/selectReviewListStats.do", "{\"targetHandle\":\"" + hostH + "\"}", null).body();
        assertEquals("1", String.valueOf((Object) JsonPath.read(stats, "$.data.reviewCnt")));
        assertTrue(String.valueOf((Object) JsonPath.read(stats, "$.data.avgRating")).startsWith("5"),
                "평균 평점: " + stats);

        // 남의 후기를 지울 수 없다
        String revId = jdbc.queryForObject(
                "SELECT review_id::text FROM t_review WHERE recruit_id = ?::integer AND reg_id = 'rvjoin'",
                String.class, rid);
        assertEquals(403, post("/api/adm/review/deleteReview.do", "{\"rowId\":\"" + revId + "\"}", out).statusCode());
        assertEquals(200, post("/api/adm/review/deleteReview.do", "{\"rowId\":\"" + revId + "\"}", join).statusCode());
    }
}
