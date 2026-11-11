package com.pwsh.domain.auth.service;

import jakarta.validation.constraints.NotBlank;

/** 관리자 2차 인증 — 비밀번호 단계에서 받은 확인 키 + 메일로 받은 6자리 코드 */
public record OtpRequest(
        @NotBlank(message = "인증 확인 키는 필수입니다.") String otpKey,
        @NotBlank(message = "인증 코드는 필수입니다.") String code) {
}
