package com.pwsh.domain.auth.service;

import jakarta.validation.constraints.NotBlank;

/**
 * 셀프 회원가입 요청. 아이디·비밀번호·닉네임 필수, 이메일은 선택.
 * (비밀번호 복잡도는 컨트롤러에서 PasswordPolicy, 중복/일치 검사는 AuthService.signup)
 */
public record SignupRequest(
        @NotBlank(message = "아이디는 필수입니다.") String userId,
        @NotBlank(message = "비밀번호는 필수입니다.") String userPw,
        @NotBlank(message = "비밀번호 확인은 필수입니다.") String pwConfirm,
        @NotBlank(message = "닉네임은 필수입니다.") String nickname,
        String email) {
}
