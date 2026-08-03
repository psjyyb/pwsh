package com.pwsh.domain.auth.service;

import jakarta.validation.constraints.NotBlank;

/** 본인 닉네임 변경 요청. */
public record NicknameRequest(
        @NotBlank(message = "닉네임은 필수입니다.") String nickname) {
}
