package com.pwsh.global.security;

import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.response.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 인증됐으나 권한 부족 → 403 + ApiResponse JSON.
 * ObjectMapper 미사용(Boot4=Jackson3 패키지 이슈 회피) — 고정 구조라 직접 직렬화.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponseWriter.write(response, ErrorCode.ACCESS_DENIED);
    }
}
