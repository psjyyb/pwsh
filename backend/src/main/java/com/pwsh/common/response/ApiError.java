package com.pwsh.common.response;

/**
 * API 오류 상세. code=에러코드, message=사용자 메시지.
 */
public record ApiError(String code, String message) {
}
