package com.pwsh.global.web;

import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.common.response.ErrorResponseWriter;
import com.pwsh.domain.config.service.ConfigService;
import com.pwsh.global.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 점검(유지보수) 모드 — 환경설정 maint_yn='Y'이면 관리자 외 모든 API를 503으로 막는다.
 *
 * <p>필터가 아니라 인터셉터인 이유: 통과 여부를 <b>로그인 주체</b>로 판단해야 하는데,
 * 인증은 Spring Security 필터 체인이 끝난 뒤에야 SecurityContext에 올라온다.
 * (예외 IP 목록으로 판정하면 필터에 둘 수 있지만, 관리자 계정 기준이 더 단순하고
 * 재택·모바일 등 IP가 바뀌는 곳에서도 점검을 풀 수 있다.)
 *
 * <p>다음은 점검 중에도 열어둔다 — 막으면 관리자가 점검을 <b>끄러 들어올 수조차 없다</b>.
 * <ul>
 *   <li>세션 관련 인증 API — 로그인·로그아웃·토큰재발급·내정보·비밀번호 변경/연장</li>
 *   <li>{@code /api/adm/config/selectConfigView.do} — 사이트명·로고(점검 화면이 읽는다)</li>
 *   <li>{@code /api/pub/**} — 로고 등 공개 이미지</li>
 * </ul>
 *
 * <p>⚠ {@code /api/auth/**}를 통째로 열지 않는 이유: 이 프로젝트의 auth에는 <b>셀프 회원가입</b>
 * (signup·sendSignupCode)·비밀번호 재설정·탈퇴·프로필 수정이 함께 있다. 점검 중에 가입이나
 * 탈퇴가 진행되면 안 되므로 <b>세션을 얻고 유지하는 엔드포인트만</b> 골라서 연다.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {

    private static final Set<String> ALLOW_EXACT = Set.of(
            "/api/adm/config/selectConfigView.do",
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/refresh",
            "/api/auth/me",
            "/api/auth/pwChange",
            "/api/auth/pwExtend");
    private static final Set<String> ALLOW_PREFIX = Set.of("/api/pub/");

    private final ConfigService configService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        ConfigService.MaintStatus maint = configService.maintStatus();
        if (!maint.on() || SecurityUtil.isAdmin()) {
            return true;
        }
        String path = request.getRequestURI();
        if (ALLOW_EXACT.contains(path)) {
            return true;
        }
        for (String prefix : ALLOW_PREFIX) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        String message = maint.message() == null || maint.message().isBlank()
                ? Messages.get("maintenance.default") : maint.message();
        ErrorResponseWriter.write(response, ErrorCode.MAINTENANCE, message);
        return false;
    }
}
