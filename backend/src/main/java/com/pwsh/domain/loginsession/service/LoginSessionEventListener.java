package com.pwsh.domain.loginsession.service;

import com.pwsh.common.event.LoginSucceededEvent;
import com.pwsh.common.event.SessionInvalidatedEvent;
import com.pwsh.domain.eventlog.service.EventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 인증 이벤트에 반응하는 곳. 인증 서비스는 "무슨 일이 있었다"만 알리고, 그 뒤처리는 여기서 한다.
 *
 * <p>{@code @Async}를 쓰지 않는다(동기). 같은 트랜잭션 안에서 돌아야
 * 발행 측이 롤백되면 세션 기록도 함께 롤백된다 — 비밀번호 변경이 실패했는데 세션만 닫히면 안 된다.
 */
@Component
@RequiredArgsConstructor
public class LoginSessionEventListener {

    private final LoginSessionService loginSessionService;
    private final EventLogService eventLogService;

    /** 로그인 성공 → 활동로그 기록 + 접속 세션 열기(이전 세션은 RELOGIN으로 닫힌다). */
    @EventListener
    public void onLoginSucceeded(LoginSucceededEvent event) {
        eventLogService.write("LOGIN", null, null);
        loginSessionService.open(event.memberId());
    }

    /** 토큰 무효화 → 접속 세션 종료. 사유는 이벤트가 실어 온 값을 그대로 남긴다. */
    @EventListener
    public void onSessionInvalidated(SessionInvalidatedEvent event) {
        loginSessionService.close(event.memberId(), event.reason());
    }
}
