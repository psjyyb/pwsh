package com.pwsh.domain.auth.service;

import jakarta.validation.constraints.NotBlank;

/** 본인 비밀번호 변경 요청 (로그인 사용자). */
public record PwChangeRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.") String currentPw,
        @NotBlank(message = "새 비밀번호는 필수입니다.") String newPw) {
}
