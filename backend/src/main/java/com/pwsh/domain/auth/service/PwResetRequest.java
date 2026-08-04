package com.pwsh.domain.auth.service;

import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 재설정 요청 — 아이디 + 이메일 인증코드 + 새 비밀번호(확인).
 * (코드 검증·비번 적용은 AuthService.resetPassword, 복잡도는 컨트롤러 PasswordPolicy)
 */
public record PwResetRequest(
        @NotBlank(message = "아이디는 필수입니다.") String userId,
        @NotBlank(message = "인증코드는 필수입니다.") String code,
        @NotBlank(message = "새 비밀번호는 필수입니다.") String newPw,
        @NotBlank(message = "비밀번호 확인은 필수입니다.") String pwConfirm) {
}
