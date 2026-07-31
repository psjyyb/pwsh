package com.pwsh.global.security;

import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.response.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 미인증 접근(토큰 없음/무효) → 401 + ApiResponse JSON.
 * (Security 기본 동작은 403이라, JWT REST에 맞게 401로 통일)
 * ObjectMapper 미사용(Boot4=Jackson3 패키지 이슈 회피) — 고정 구조라 직접 직렬화.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorResponseWriter.write(response, ErrorCode.UNAUTHORIZED);
    }
}
