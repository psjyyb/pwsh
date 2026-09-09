package com.pwsh.common.event;

/**
 * 접속 세션 종료 사유. {@code login_session.end_reason}에 그대로 저장된다.
 *
 * <p>발행 측(토큰을 무효화하는 도메인)과 기록 측(접속 세션 도메인)이 같은 값을 써야 하므로
 * 어느 한쪽 도메인이 아니라 이벤트 패키지에 둔다 — 이 상수를 쓰려고 도메인 서비스를
 * import 하게 되면 이벤트로 끊어낸 결합이 되살아난다.
 *
 * <p>화면 표시 문구는 프론트의 `END_REASON_LABEL`에 있다(여기 값을 추가하면 그쪽도 함께 추가).
 */
public final class SessionEndReason {

    /** 본인 로그아웃 */
    public static final String LOGOUT = "LOGOUT";
    /** 관리자 강제종료 */
    public static final String FORCE = "FORCE";
    /** 다른 기기 새 로그인(단일세션 last-wins) */
    public static final String RELOGIN = "RELOGIN";
    /** 비밀번호 변경·관리자 리셋·비밀번호 재설정 */
    public static final String PWCHANGE = "PWCHANGE";

    // ↓ 이 서비스에만 있는 사유(CMS 틀에는 탈퇴·계정정지 기능이 없다)
    /** 셀프 탈퇴 */
    public static final String WITHDRAW = "WITHDRAW";
    /** 관리자 계정 정지 */
    public static final String SUSPEND = "SUSPEND";

    private SessionEndReason() {
    }
}
