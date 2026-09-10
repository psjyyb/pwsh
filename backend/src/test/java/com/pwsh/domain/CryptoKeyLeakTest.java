package com.pwsh.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pwsh.common.BaseVO;
import com.pwsh.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * 개인정보 대칭키(pgcrypto)가 API 응답에 새어나가지 않는지 — 회귀 방지.
 *
 * <p>{@code BaseVO.getCryptoKey()}는 MyBatis {@code #{cryptoKey}} 바인딩용 getter다.
 * getter라서 Jackson이 프로퍼티로 보고 <b>모든 VO 응답에 키를 직렬화</b>하므로
 * {@code @JsonIgnore}로 막아뒀다. 그 한 줄은 지워도 컴파일과 기존 테스트가 전부 통과하기 때문에
 * (바탕 CMS 쪽에서 실제로 빠져 있었고, 비로그인 응답에 키가 그대로 실려 나갔다)
 * 응답 본문을 직접 훑는 테스트로 못을 박는다.
 *
 * <p>여기는 셀프 가입 서비스라 회원 개인정보(실명·연락처·이메일·생일)가 실제로 쌓인다 —
 * 키가 새면 DB 유출 시 복호화까지 가능해져 피해가 훨씬 커진다.
 */
class CryptoKeyLeakTest extends IntegrationTest {

    /** 실제 키 값 — 프로퍼티 이름을 바꿔 우회하는 경우까지 잡기 위해 값 자체도 검사한다. */
    private static final String KEY_VALUE = new BaseVO().getCryptoKey();

    private void assertNoKey(String label, String body) {
        assertFalse(body.contains("cryptoKey"), label + " 응답에 cryptoKey 필드가 있다: " + body);
        assertFalse(body.contains(KEY_VALUE), label + " 응답에 키 값이 그대로 실려 있다: " + body);
    }

    @Test
    void public_endpoints_do_not_expose_crypto_key() throws Exception {
        // SecurityConfig permitAll 경로 — 노출되면 비로그인 누구나 키를 얻으므로 피해가 가장 크다
        assertNoKey("config(비로그인)", post("/api/adm/config/selectConfigView.do", "{}", null).body());
        assertNoKey("menu tree(비로그인)", post("/api/adm/menu/selectMenuListTree.do", "{\"area\":\"GEN\"}", null).body());
        assertNoKey("popup(비로그인)", post("/api/adm/popup/selectPopupListMain.do", "{}", null).body());
        assertNoKey("모집 목록(비로그인)", post("/api/adm/recruit/selectRecruitList.do", "{}", null).body());
        assertNoKey("게시글 목록(비로그인)", post("/api/adm/post/selectPostList.do", "{}", null).body());

        // 공개 프로필 — 회원을 지목하는 공개 API라 PII 인접 경로다(조회 키는 handle)
        String handle = jdbc.queryForObject("SELECT handle FROM member WHERE member_id = 'user'", String.class);
        assertNoKey("공개 프로필(비로그인)", post("/api/auth/memberProfile", "{\"handle\":\"" + handle + "\"}", null).body());
    }

    @Test
    void admin_endpoints_do_not_expose_crypto_key() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        // 개인정보를 복호화해 내려주는 화면이라 키가 같은 VO에 실릴 위험이 가장 높다
        assertNoKey("member 목록", post("/api/adm/member/selectMemberList.do", "{}", admin).body());
        assertNoKey("member 상세", post("/api/adm/member/selectMemberView.do", "{\"rowId\":\"admin\"}", admin).body());
        assertNoKey("board 목록", post("/api/adm/board/selectBoardList.do", "{}", admin).body());
        assertNoKey("code 목록", post("/api/adm/code/selectCodeList.do", "{}", admin).body());
        assertNoKey("eventlog 목록", post("/api/adm/eventlog/selectEventlogList.do", "{}", admin).body());
        assertNoKey("접속세션 목록", post("/api/adm/loginsession/selectLoginSessionList.do", "{}", admin).body());
    }

    @Test
    void member_endpoint_does_not_expose_crypto_key() throws Exception {
        String user = accessToken("user", "user1234!");
        assertNoKey("내 정보", post("/api/auth/me", "{}", user).body());
    }

    @Test
    void crypto_key_still_works_for_decryption() throws Exception {
        // 직렬화만 막았을 뿐이라 매퍼의 복호화는 그대로 동작해야 한다.
        // (getter를 지우거나 이름을 바꿔 '해결'하면 여기서 깨진다)
        String admin = accessToken("admin", "admin1234!");
        var res = post("/api/adm/member/selectMemberView.do", "{\"rowId\":\"admin\"}", admin);
        assertEquals(200, res.statusCode());
        // 이름은 pgcrypto AES로 저장돼 있다 → 복호화가 되면 사람이 읽는 값이 나온다
        assertTrue(res.body().contains("memberName"), "복호화된 이름 필드가 응답에 있어야 한다: " + res.body());
        assertFalse(res.body().contains("\\x"), "복호화가 안 되면 HEX 원문이 그대로 나온다: " + res.body());
    }
}
