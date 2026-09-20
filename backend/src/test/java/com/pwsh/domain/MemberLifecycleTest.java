package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.domain.member.service.MemberService;
import com.pwsh.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 회원 라이프사이클 — 휴면 전환 · 사전 통지 · 탈퇴 후 개인정보 파기.
 *
 * <p>둘 다 <b>배치가 조용히 도는 기능</b>이라 잘못돼도 화면에 아무 표시가 없다.
 * 안 돌면 개인정보가 영원히 남고, 과하게 돌면 멀쩡한 계정이 잠긴다. 양쪽을 다 고정한다.
 *
 * <p>이 저장소만의 차이: 작성자 표기가 닉네임이라 파기해도 닉네임은 비우지 않고
 * '탈퇴한 회원'으로 바꾼다(NULL로 두면 과거 글·모집의 작성자가 빈칸이 된다).
 */
class MemberLifecycleTest extends IntegrationTest {

    private static final String PW = "Test1234!@";

    @Autowired
    private MemberService memberService;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM auth_member WHERE member_id LIKE 'zzlc%'");
        jdbc.update("DELETE FROM login_session WHERE member_id LIKE 'zzlc%'");
        jdbc.update("DELETE FROM member WHERE member_id LIKE 'zzlc%'");
        jdbc.update("DELETE FROM mail_log WHERE template_cd = 'DORMANT_NOTICE'");
        jdbc.update("UPDATE config SET dormant_days = 365, dormant_notify_days = 30, destroy_days = 30");
    }

    @Test
    @DisplayName("장기 미접속 계정은 휴면으로 바뀌고, 그 순간 세션이 끊긴다")
    void dormantSweepInvalidatesSession() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        assertThat(createMember(admin, "zzlc1").statusCode()).isEqualTo(200);
        int verBefore = tokenVer("zzlc1");
        backdateLogin("zzlc1", 400);

        assertThat(memberService.sweepDormant()).isPositive();

        assertThat(statusOf("zzlc1")).isEqualTo("STATUS04");
        assertThat(jdbc.queryForObject(
                "SELECT dormant_dt IS NOT NULL FROM member WHERE member_id = 'zzlc1'", Boolean.class)).isTrue();
        // token_ver가 올라가야 이미 로그인해 있던 기기의 토큰이 다음 요청에서 죽는다
        assertThat(tokenVer("zzlc1")).as("휴면 전환은 발급된 토큰을 무효화해야 한다").isGreaterThan(verBefore);
    }

    @Test
    @DisplayName("휴면 계정은 비밀번호가 맞아도 로그인되지 않는다")
    void dormantCannotLogin() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlc2");
        assertThat(login("zzlc2", PW).statusCode()).as("전환 전에는 로그인된다").isEqualTo(200);

        backdateLogin("zzlc2", 400);
        memberService.sweepDormant();

        assertThat(login("zzlc2", PW).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("휴면을 해제하면 다시 로그인되고, 다음 배치에서 곧바로 재휴면되지 않는다")
    void restoreWorksAndDoesNotImmediatelyRelapse() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlc3");
        backdateLogin("zzlc3", 400);
        memberService.sweepDormant();

        assertThat(post("/api/adm/member/updateMemberRestore.do",
                "{\"memberId\":\"zzlc3\"}", admin).statusCode()).isEqualTo(200);
        assertThat(statusOf("zzlc3")).isEqualTo("STATUS01");

        // ★ 로그인하기 전에 배치를 돌린다 — 로그인하면 last_login_dt가 갱신돼
        //   "해제가 접속일을 당겼는가"를 확인할 수 없게 된다. 안 당기면 여기서 바로 재휴면된다.
        memberService.sweepDormant();
        assertThat(statusOf("zzlc3")).as("해제 직후 배치가 돌아도 다시 휴면이 되면 안 된다").isEqualTo("STATUS01");

        assertThat(login("zzlc3", PW).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("dormant_days=0이면 휴면 전환 기능이 꺼진다")
    void dormantDaysZeroDisablesSweep() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlc4");
        backdateLogin("zzlc4", 4000);
        jdbc.update("UPDATE config SET dormant_days = 0");

        memberService.sweepDormant();

        assertThat(statusOf("zzlc4")).isEqualTo("STATUS01");
    }

    @Test
    @DisplayName("휴면 전환 예정 안내메일이 나가고 발송 이력이 남는다")
    void dormantNoticeIsSent() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlc5");
        setEmail(admin, "zzlc5", "zzlc5@example.com");
        // 전환일(365) - 통지일(30) = 335일 전 접속자가 오늘 안내 대상이다
        backdateLogin("zzlc5", 335);

        assertThat(memberService.notifyDormantSoon()).isPositive();

        String subject = jdbc.queryForObject(
                "SELECT subject FROM mail_log WHERE template_cd = 'DORMANT_NOTICE' ORDER BY mail_log_id DESC LIMIT 1",
                String.class);
        assertThat(subject).contains("휴면");
        String content = jdbc.queryForObject(
                "SELECT content FROM mail_log WHERE template_cd = 'DORMANT_NOTICE' ORDER BY mail_log_id DESC LIMIT 1",
                String.class);
        // 마커가 그대로 남으면 수신자가 {{dormantDt}}를 보게 된다
        assertThat(content).doesNotContain("{{").contains("테스트");
    }

    @Test
    @DisplayName("탈퇴 후 보존기간이 지나면 개인정보 컬럼만 비우고 행은 남긴다")
    void destroyClearsPersonalColumnsOnly() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlc6");
        setEmail(admin, "zzlc6", "zzlc6@example.com");
        String handleBefore = jdbc.queryForObject(
                "SELECT handle FROM member WHERE member_id = 'zzlc6'", String.class);

        assertThat(post("/api/adm/member/deleteMember.do", "{\"rowId\":\"zzlc6\"}", admin).statusCode())
                .isEqualTo(200);
        jdbc.update("UPDATE member SET withdraw_dt = NOW() - INTERVAL '60 days' WHERE member_id = 'zzlc6'");

        assertThat(memberService.sweepDestroy()).isPositive();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT member_id, handle, name, phone, email, birth, nickname, profile_file_id,"
                        + " password, use_yn, destroy_dt FROM member WHERE member_id = 'zzlc6'");
        assertThat(row).as("행 자체는 남아야 한다 — 지우면 과거 게시글의 작성자가 깨진다").isNotNull();
        assertThat(row.get("name")).isNull();
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("email")).isNull();
        assertThat(row.get("birth")).isNull();
        assertThat(row.get("profile_file_id")).as("얼굴 사진도 개인정보다").isNull();
        assertThat(row.get("password")).isEqualTo("");
        assertThat(row.get("use_yn")).as("개인정보가 사라진 계정을 살려 두지 않는다").isEqualTo("N");
        assertThat(row.get("destroy_dt")).isNotNull();
        // ★ 닉네임은 비우지 않는다 — 이 서비스는 작성자를 닉네임으로만 표기한다
        assertThat(row.get("nickname")).isEqualTo("탈퇴한 회원");
        // 작성자 지목에 쓰는 키는 남긴다(비우면 유니크 제약과 기존 링크가 함께 깨진다)
        assertThat(row.get("member_id")).isEqualTo("zzlc6");
        assertThat(row.get("handle")).isEqualTo(handleBefore);
    }

    @Test
    @DisplayName("보존기간이 남은 탈퇴 계정은 아직 파기하지 않는다")
    void destroyWaitsForRetention() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlc7");
        post("/api/adm/member/deleteMember.do", "{\"rowId\":\"zzlc7\"}", admin);
        // 탈퇴 직후(보존기간 30일 미경과)

        memberService.sweepDestroy();

        assertThat(jdbc.queryForObject(
                "SELECT destroy_dt IS NULL FROM member WHERE member_id = 'zzlc7'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT name IS NOT NULL FROM member WHERE member_id = 'zzlc7'", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("관리자는 보존기간을 기다리지 않고 즉시 파기할 수 있고, 두 번 파기되지 않는다")
    void destroyNowIsIdempotent() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlc8");

        assertThat(post("/api/adm/member/updateMemberDestroy.do",
                "{\"memberId\":\"zzlc8\"}", admin).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "SELECT name IS NULL FROM member WHERE member_id = 'zzlc8'", Boolean.class)).isTrue();

        // 이미 파기된 계정을 또 파기하면 destroy_dt가 덮여 "언제 파기했는지"가 사라진다
        assertThat(post("/api/adm/member/updateMemberDestroy.do",
                "{\"memberId\":\"zzlc8\"}", admin).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("이름이 같은 '탈퇴한 회원'이 여럿 생겨도 파기가 실패하지 않는다")
    void destroyTwoMembersDoesNotCollideOnNickname() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlca");
        createMember(admin, "zzlcb");
        setNickname("zzlca", "닉네임A");
        setNickname("zzlcb", "닉네임B");

        assertThat(post("/api/adm/member/updateMemberDestroy.do",
                "{\"memberId\":\"zzlca\"}", admin).statusCode()).isEqualTo(200);
        // 부분 유니크 인덱스(ux_member_nickname)가 use_yn='Y'만 걸리므로 계정을 내려야 두 번째가 통과한다
        assertThat(post("/api/adm/member/updateMemberDestroy.do",
                "{\"memberId\":\"zzlcb\"}", admin).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("즉시 파기는 접속 중이던 세션을 '개인정보 파기' 사유로 끊는다")
    void destroyNowEndsSessionWithDestroyReason() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlcd");
        accessToken("zzlcd", PW); // 접속 세션 생성
        int verBefore = tokenVer("zzlcd");

        assertThat(post("/api/adm/member/updateMemberDestroy.do",
                "{\"memberId\":\"zzlcd\"}", admin).statusCode()).isEqualTo(200);

        // JWT 필터는 상태가 아니라 token_ver로 판정한다 — 안 올리면 기존 토큰이 만료까지 살아 있다
        assertThat(tokenVer("zzlcd")).as("파기는 발급된 토큰을 무효화해야 한다").isGreaterThan(verBefore);
        assertThat(jdbc.queryForObject(
                "SELECT end_reason FROM login_session WHERE member_id = 'zzlcd'"
                        + " ORDER BY login_session_id DESC LIMIT 1", String.class))
                .as("접속이력에 탈퇴가 아니라 파기로 남아야 구분이 된다").isEqualTo("DESTROY");
    }

    @Test
    @DisplayName("사용자 폼에서 휴면을 정상으로 바꿔도 다음 배치에서 다시 휴면이 되지 않는다")
    void formStatusChangeOutOfDormantDoesNotRelapse() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlce");
        backdateLogin("zzlce", 400);
        memberService.sweepDormant();
        assertThat(statusOf("zzlce")).isEqualTo("STATUS04");

        // 휴면해제 버튼이 아니라 상세 폼의 계정상태 항목으로 되돌리는 경로
        assertThat(post("/api/adm/member/updateMember.do",
                "{\"rowId\":\"zzlce\",\"memberName\":\"테스트\",\"typeCd\":\"MEM01\","
                        + "\"statusCd\":\"STATUS01\"}", admin).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "SELECT dormant_dt IS NULL FROM member WHERE member_id = 'zzlce'", Boolean.class)).isTrue();

        memberService.sweepDormant();

        assertThat(statusOf("zzlce")).as("폼으로 되돌린 계정이 곧바로 재휴면되면 안 된다").isEqualTo("STATUS01");
    }

    @Test
    @DisplayName("사용자 폼에서 휴면으로 바꾸면 전환 시각이 찍히고 세션이 끊긴다")
    void formStatusChangeIntoDormantKillsSession() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlcf");
        int verBefore = tokenVer("zzlcf");

        assertThat(post("/api/adm/member/updateMember.do",
                "{\"rowId\":\"zzlcf\",\"memberName\":\"테스트\",\"typeCd\":\"MEM01\","
                        + "\"statusCd\":\"STATUS04\"}", admin).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject(
                "SELECT dormant_dt IS NOT NULL FROM member WHERE member_id = 'zzlcf'", Boolean.class)).isTrue();
        assertThat(tokenVer("zzlcf")).as("폼으로 휴면 전환해도 배치와 똑같이 토큰을 끊어야 한다")
                .isGreaterThan(verBefore);
        assertThat(login("zzlcf", PW).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("제재 해제로 휴면을 풀어도 다음 배치에서 다시 휴면이 되지 않는다")
    void sanctionReleaseFromDormantDoesNotRelapse() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlcg");
        backdateLogin("zzlcg", 400);
        memberService.sweepDormant();
        assertThat(statusOf("zzlcg")).isEqualTo("STATUS04");

        // 이 서비스에만 있는 경로 — 제재 토글(updateStatus)로도 STATUS01이 될 수 있다
        assertThat(post("/api/adm/member/updateMemberStatus.do",
                "{\"memberId\":\"zzlcg\",\"statusCd\":\"STATUS01\"}", admin).statusCode()).isEqualTo(200);

        memberService.sweepDormant();

        assertThat(statusOf("zzlcg")).as("제재 해제로 푼 계정이 곧바로 재휴면되면 안 된다").isEqualTo("STATUS01");
    }

    @Test
    @DisplayName("파기해도 그 회원이 쓴 게시글의 작성자 연결과 표기는 남는다")
    void destroyKeepsPostAuthorLink() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        createMember(admin, "zzlc9");
        // 게시글을 그 회원 이름으로 만들어 둔다(작성자=reg_id)
        String postId = com.jayway.jsonpath.JsonPath.read(post("/api/adm/post/insertPost.do",
                "{\"boardId\":\"1\",\"title\":\"ZZ파기테스트\",\"content\":\"본문\"}", admin).body(), "$.data");
        jdbc.update("UPDATE post SET reg_id = 'zzlc9' WHERE post_id = ?::integer", Integer.parseInt(postId));

        post("/api/adm/member/updateMemberDestroy.do", "{\"memberId\":\"zzlc9\"}", admin);

        assertThat(jdbc.queryForObject(
                "SELECT reg_id FROM post WHERE post_id = ?::integer", String.class, Integer.parseInt(postId)))
                .as("작성자 ID가 살아 있어야 게시글이 유령이 되지 않는다").isEqualTo("zzlc9");
        // 사용자 화면의 작성자 표기는 닉네임 서브쿼리다 — 비면 빈칸으로 렌더된다
        assertThat(jdbc.queryForObject(
                "SELECT (SELECT u.nickname FROM member u WHERE u.member_id = p.reg_id)"
                        + " FROM post p WHERE p.post_id = ?::integer", String.class, Integer.parseInt(postId)))
                .isEqualTo("탈퇴한 회원");

        jdbc.update("DELETE FROM post WHERE post_id = ?::integer", Integer.parseInt(postId));
    }

    // ===== helpers =====

    private String statusOf(String memberId) {
        return jdbc.queryForObject("SELECT status_cd FROM member WHERE member_id = ?", String.class, memberId);
    }

    private int tokenVer(String memberId) {
        Integer v = jdbc.queryForObject("SELECT token_ver FROM member WHERE member_id = ?", Integer.class, memberId);
        return v == null ? 0 : v;
    }

    /** 마지막 접속(과 가입일)을 과거로 옮긴다 — 배치가 기간을 어떻게 보는지 확인하기 위함. */
    private void backdateLogin(String memberId, int days) {
        jdbc.update("UPDATE member SET last_login_dt = NOW() - (? * INTERVAL '1 day'),"
                + " reg_dt = NOW() - (? * INTERVAL '1 day') WHERE member_id = ?", days, days, memberId);
    }

    private void setNickname(String memberId, String nickname) {
        jdbc.update("UPDATE member SET nickname = ? WHERE member_id = ?", nickname, memberId);
    }

    private void setEmail(String token, String memberId, String email) throws Exception {
        assertThat(post("/api/adm/member/updateMember.do",
                "{\"rowId\":\"" + memberId + "\",\"memberName\":\"테스트\",\"typeCd\":\"MEM01\","
                        + "\"statusCd\":\"STATUS01\",\"email\":\"" + email + "\"}", token).statusCode())
                .isEqualTo(200);
    }
}
