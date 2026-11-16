package com.pwsh.domain.auth.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 본인 닉네임 변경 요청. 30자는 member.nickname 길이 — 넘으면 DB 오류(500)가 나던 것을 400으로(CMS 1.2.37). */
public record NicknameRequest(
        @NotBlank(message = "닉네임은 필수입니다.") @Size(max = 30, message = "닉네임은 30자까지입니다.") String nickname) {
}
