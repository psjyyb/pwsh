package com.pwsh.common.response;

import com.pwsh.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 서블릿 계층(필터/인터셉터/시큐리티 핸들러)에서 ApiResponse 형태의 에러 JSON을 직접 기록한다.
 * ObjectMapper 미사용(Boot4=Jackson3 패키지 이슈 회피)이라 직접 직렬화하되,
 * 여러 곳에 흩어져 있던 동일 JSON 문자열을 이 헬퍼 한 곳으로 통일한다(형태 일관성 + 이스케이프).
 * 형태: {"success":false,"data":null,"error":{"code":...,"message":...}} (GlobalExceptionHandler 응답과 동일)
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {}

    public static void write(HttpServletResponse response, ErrorCode ec) throws IOException {
        response.setStatus(ec.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + ec.getCode()
                        + "\",\"message\":\"" + escape(ec.getMessage()) + "\"}}");
    }

    /** JSON 문자열 이스케이프(메시지에 \ 또는 " 포함 대비) */
    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
