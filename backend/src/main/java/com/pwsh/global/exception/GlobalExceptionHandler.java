package com.pwsh.global.exception;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.response.ApiError;
import com.pwsh.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리. 모든 예외를 표준 ApiResponse로 변환. (설정성이라 global에 위치)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 업무 예외 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("BusinessException: {} - {}", ec.getCode(), e.getMessage());
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(new ApiError(ec.getCode(), e.getMessage())));
    }

    /** 인증 실패 (로그인 아이디/비번 불일치 등) */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        ErrorCode ec = ErrorCode.UNAUTHORIZED;
        log.warn("AuthenticationException: {}", e.getMessage());
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(new ApiError(ec.getCode(), "아이디 또는 비밀번호가 올바르지 않습니다.")));
    }

    /** Bean Validation(@Valid) 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.INVALID_INPUT.getMessage();
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(new ApiError(ec.getCode(), message)));
    }

    /** 존재하지 않는 경로 (핸들러/정적리소스 없음) → 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        ErrorCode ec = ErrorCode.RESOURCE_NOT_FOUND;
        log.warn("No handler: {}", e.getMessage());
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(new ApiError(ec.getCode(), ec.getMessage())));
    }

    /** 그 외 모든 예외 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(new ApiError(ec.getCode(), ec.getMessage())));
    }
}
