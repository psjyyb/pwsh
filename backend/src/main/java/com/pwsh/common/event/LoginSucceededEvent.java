package com.pwsh.common.event;

/**
 * 로그인 성공. 토큰 발급이 끝난 뒤 발행한다.
 *
 * <p>로그인에 반응해야 하는 곳이 여러 개다(활동로그 기록, 접속 세션 열기, 앞으로는 알림·통계 등).
 * 인증 서비스가 그것들을 하나씩 직접 부르면 관심사가 계속 늘어붙으므로 이벤트로 알리고,
 * 반응은 각 도메인의 리스너가 맡는다.
 *
 * <p>리스너는 {@code @EventListener}(동기)로 받는다 — 같은 스레드·같은 트랜잭션에서 돌아
 * 발행 측이 롤백되면 반응도 함께 롤백된다.
 */
public record LoginSucceededEvent(String memberId) {
}
