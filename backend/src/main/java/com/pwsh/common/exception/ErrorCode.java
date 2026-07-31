package com.pwsh.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 표준 에러코드. (code = C=공통, A=인증/인가 ...) 도메인별 에러는 접두어를 나눠 확장.
 */
@Getter
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 요청입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "요청한 리소스를 찾을 수 없습니다."),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "C004", "비밀번호는 8~64자이며 영문·숫자·특수문자를 모두 포함해야 합니다."),
    DUPLICATE(HttpStatus.CONFLICT, "C005", "이미 사용 중인 값입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C500", "서버 오류가 발생했습니다."),

    // 인증/인가
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "만료된 토큰입니다."),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "A005", "로그인 실패가 반복되어 계정이 잠겼습니다. 잠시 후 다시 시도하세요."),
    ACCOUNT_SUSPENDED(HttpStatus.UNAUTHORIZED, "A006", "정지된 계정입니다. 관리자에게 문의하세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A007", "아이디 또는 비밀번호가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
