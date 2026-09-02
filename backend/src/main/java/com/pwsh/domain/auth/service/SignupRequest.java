package com.pwsh.domain.auth.service;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 셀프 회원가입 요청. 아이디·비밀번호·닉네임·이메일·인증코드 필수.
 * (이메일 인증코드 검증·중복/일치 검사는 AuthService.signup, 비밀번호 복잡도는 컨트롤러 PasswordPolicy)
 *
 * agreedPolicyIds: 동의한 약관 ID. 필수동의(policy.req_yn='Y') 약관이 전부 포함돼야 가입된다.
 *   화면 체크박스만으로는 우회가 가능하므로 서버(AuthService.signup)에서 다시 판정한다.
 */
public record SignupRequest(
        @NotBlank(message = "아이디는 필수입니다.") String memberId,
        @NotBlank(message = "비밀번호는 필수입니다.") String password,
        @NotBlank(message = "비밀번호 확인은 필수입니다.") String pwConfirm,
        @NotBlank(message = "닉네임은 필수입니다.") String nickname,
        @NotBlank(message = "이메일은 필수입니다.") String email,
        @NotBlank(message = "이메일 인증코드는 필수입니다.") String code,
        List<String> agreedPolicyIds) {
}
