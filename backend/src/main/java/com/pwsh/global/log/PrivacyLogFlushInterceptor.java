package com.pwsh.global.log;

import com.pwsh.domain.privacylog.service.PrivacyLogService;
import com.pwsh.global.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 요청이 끝나면 이번 요청에서 모인 개인정보 조회를 {@code privacy_log} 1건으로 남긴다.
 *
 * <p>{@code afterCompletion}에서 남기는 이유: 업무 트랜잭션이 이미 끝난 뒤라 <b>롤백돼도 기록은 남는다</b>.
 * 조회 도중에 넣으면 그 트랜잭션과 운명을 같이한다.
 *
 * <p>비로그인 요청은 남기지 않는다. 이 틀에서 미인증 상태로 개인정보를 읽는 경로는 로그인 인증뿐이고
 * (공개 API는 공개 식별자 규칙상 개인정보를 내보내지 않는다), 로그인은 event_log가 이미 남긴다.
 * 여기까지 남기면 접근기록이 로그인 기록으로 뒤덮여 정작 봐야 할 조회가 묻힌다.
 */
@Component
@RequiredArgsConstructor
public class PrivacyLogFlushInterceptor implements HandlerInterceptor {

    private final PrivacyLogService privacyLogService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            PrivacyAccessCollector.Access access = PrivacyAccessCollector.get();
            if (access == null || !SecurityUtil.isAuthenticated()) {
                return;
            }
            PrivacyAccessCollector.Summary summary = access.summarize(SecurityUtil.getCurrentMemberId());
            if (summary != null) {
                privacyLogService.write(request.getRequestURI(), summary);
            }
        } finally {
            // 스레드 풀이 재사용되므로 반드시 비운다 — 안 비우면 다음 요청 기록에 섞인다
            PrivacyAccessCollector.clear();
        }
    }
}
