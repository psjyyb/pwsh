package com.pwsh.common.event;

/**
 * 어떤 사용자의 발급된 토큰이 무효화됨(token_ver 증가).
 *
 * <p>이 이벤트를 만든 이유가 있다. 토큰을 죽이는 곳은 로그아웃·비밀번호 변경·관리자 리셋·강제
 * 로그아웃처럼 여러 군데인데, 그때마다 <b>접속 세션도 닫아 줘야</b> 한다. 짝을 손으로 맞추다
 * 한 곳을 빠뜨리면 "토큰은 죽었는데 접속 현황에는 계속 접속중으로 남는" 상태가 되고,
 * 컴파일러가 잡아주지 않는다.
 *
 * <p>그래서 토큰 무효화 창구를 {@code MemberService.invalidateToken} 하나로 모으고 거기서만
 * 이 이벤트를 발행한다. 이후 반응(세션 닫기 등)은 리스너가 알아서 하므로 호출부는 잊을 수가 없다.
 *
 * @param memberId 대상 사용자
 * @param reason   무효화 사유. {@code LoginSessionService.END_*} 값을 그대로 쓴다
 *                 (세션 종료 사유로 그대로 기록되므로 별도 코드 체계를 두지 않는다)
 */
public record SessionInvalidatedEvent(String memberId, String reason) {
}
