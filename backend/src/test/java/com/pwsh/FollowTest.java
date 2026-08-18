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
 * 회원 팔로우 검증.
 * - 토글(팔로우/해제)과 목록·카운트가 맞물려 움직이는지
 * - 자기 자신·비로그인은 막히는지
 * - 팔로우한 회원이 새 모집을 열면 알림이 오는지(취미를 담지 않았어도)
 * - 팔로우한 회원의 글·모집이 내 피드에 뜨고 출처가 FOLLOW로 표시되는지
 * 실서버 + 실 PostgreSQL, 모킹 0.
 */
class FollowTest extends IntegrationTest {

    @Test
    void follow_toggle_notification_and_feed() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyNm\":\"팔로우취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);
        String boardId = jdbc.queryForObject(
                "SELECT bbsinfo_id::text FROM t_hobby WHERE hobby_id = ?::integer", String.class, hobbyId);

        assertEquals(200, signup("fwhost", "팔로우대상", "fwhost@test.local").statusCode());
        assertEquals(200, signup("fwfan", "팬", "fwfan@test.local").statusCode());
        String host = accessToken("fwhost", "Test1234!@");
        String fan = accessToken("fwfan", "Test1234!@");
        String hostHandle = handleOf("fwhost");

        // 팔로우 전: 목록 비어 있고 카운트 0
        assertEquals("N", check(hostHandle, fan));
        assertEquals(0, ((List<?>) JsonPath.read(
                post("/api/adm/follow/selectFollowList.do", "{}", fan).body(), "$.data")).size());

        // 팔로우 → 목록 1건, 카운트 반영, 상대의 팔로워 목록에도 등장
        assertEquals("Y", JsonPath.read(post("/api/adm/follow/updateFollowToggle.do",
                "{\"followeeHandle\":\"" + hostHandle + "\"}", fan).body(), "$.data.followedYn"));
        assertEquals("Y", check(hostHandle, fan));
        assertEquals(1, ((List<?>) JsonPath.read(
                post("/api/adm/follow/selectFollowList.do", "{}", fan).body(), "$.data")).size());
        assertEquals("1", String.valueOf(JsonPath.<Object>read(
                post("/api/adm/follow/selectFollowListCounts.do",
                        "{\"followeeHandle\":\"" + hostHandle + "\"}", fan).body(), "$.data.followerCnt")));
        assertEquals(1, ((List<?>) JsonPath.read(
                post("/api/adm/follow/selectFollowListFollowers.do", "{}", host).body(), "$.data")).size());

        // 자기 자신·비로그인은 차단
        assertNotEquals(200, post("/api/adm/follow/updateFollowToggle.do",
                "{\"followeeHandle\":\"" + hostHandle + "\"}", host).statusCode(), "자기 자신 팔로우 차단");
        assertNotEquals(200, post("/api/adm/follow/updateFollowToggle.do",
                "{\"followeeHandle\":\"" + hostHandle + "\"}", null).statusCode(), "비로그인 차단");

        // 팔로우한 회원이 새 모집을 열면 알림 — 팬은 그 취미를 담지 않았는데도 받는다
        int before = notiCnt("fwfan");
        String future = LocalDate.now().plusDays(7).toString();
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"팔로워용 모집\",\"meetDt\":\"" + future + "\"}",
                host).body(), "$.data");
        assertEquals(before + 1, notiCnt("fwfan"), "팔로워에게 새 모집 알림 1건");
        assertEquals("/gen/recruit/" + rid, lastNotiLink("fwfan"), "알림은 그 모집으로 연결");

        // 팔로우한 회원의 글·모집이 피드에 뜨고, 출처가 FOLLOW로 표시된다
        String bbsId = JsonPath.read(post("/api/adm/bbs/insertBbs.do",
                "{\"bbsinfoId\":\"" + boardId + "\",\"title\":\"팔로워용 글\",\"context\":\"본문\"}",
                host).body(), "$.data");
        String feed = post("/api/adm/feed/selectFeedList.do", "{\"pageNo\":1,\"pageSize\":50}", fan).body();
        // rowId는 유형별 시퀀스라 글 id와 모집 id가 우연히 같을 수 있다 → feedType까지 함께 걸러야 한다
        List<Object> srcOfRecruit = JsonPath.read(feed,
                "$.data.list[?(@.rowId=='" + rid + "' && @.feedType=='RECRUIT')].feedSrc");
        List<Object> srcOfPost = JsonPath.read(feed,
                "$.data.list[?(@.rowId=='" + bbsId + "' && @.feedType=='BBS')].feedSrc");
        assertEquals(1, srcOfRecruit.size(), "팔로우한 회원의 모집이 피드에 있다");
        assertEquals("FOLLOW", srcOfRecruit.get(0));
        assertEquals(1, srcOfPost.size(), "팔로우한 회원의 글이 피드에 있다");
        assertEquals("FOLLOW", srcOfPost.get(0));

        // 팔로우 해제 → 목록·카운트·피드에서 모두 빠진다
        assertEquals("N", JsonPath.read(post("/api/adm/follow/updateFollowToggle.do",
                "{\"followeeHandle\":\"" + hostHandle + "\"}", fan).body(), "$.data.followedYn"));
        assertEquals(0, ((List<?>) JsonPath.read(
                post("/api/adm/follow/selectFollowList.do", "{}", fan).body(), "$.data")).size());
        String feedAfter = post("/api/adm/feed/selectFeedList.do", "{\"pageNo\":1,\"pageSize\":50}", fan).body();
        assertTrue(((List<?>) JsonPath.read(feedAfter,
                        "$.data.list[?(@.rowId=='" + rid + "' && @.feedType=='RECRUIT')]")).isEmpty(),
                "해제하면 피드에서도 사라진다");

        post("/api/adm/bbs/deleteBbs.do", "{\"rowId\":\"" + bbsId + "\"}", host);
    }

    private String check(String handle, String token) throws Exception {
        return JsonPath.read(post("/api/adm/follow/selectFollowListCheck.do",
                "{\"followeeHandle\":\"" + handle + "\"}", token).body(), "$.data");
    }

    private String handleOf(String userId) {
        return jdbc.queryForObject("SELECT handle FROM t_user WHERE user_id = ?", String.class, userId);
    }

    private int notiCnt(String userId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_notification WHERE user_id = ? AND use_yn = 'Y'", Integer.class, userId);
        return n == null ? 0 : n;
    }

    private String lastNotiLink(String userId) {
        return jdbc.queryForObject(
                "SELECT link_url FROM t_notification WHERE user_id = ? ORDER BY noti_id DESC LIMIT 1",
                String.class, userId);
    }
}
