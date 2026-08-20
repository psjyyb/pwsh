package com.pwsh.domain.auth.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PasswordPolicy;
import com.pwsh.domain.auth.service.AuthService;
import com.pwsh.domain.auth.service.LoginRequest;
import com.pwsh.domain.auth.service.NicknameRequest;
import com.pwsh.domain.auth.service.PwChangeRequest;
import com.pwsh.domain.auth.service.PwResetRequest;
import com.pwsh.domain.auth.service.RefreshRequest;
import com.pwsh.domain.auth.service.SignupRequest;
import com.pwsh.domain.auth.service.TokenResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API — 컨트롤러는 매핑·입력검증(@Valid, PasswordPolicy)만, 로직은 {@link AuthService}.
 * 로그인 → JWT(Access/Refresh), Access 만료 시 Refresh로 재발급. (CRUD 틀 아님 — 특수)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** 가입 이메일 인증코드 발송(공개) — 입력 이메일로 6자리 코드 발송. */
    @PostMapping("/sendSignupCode")
    public ApiResponse<Void> sendSignupCode(@RequestBody Map<String, String> body) {
        authService.sendSignupCode(body.get("email"));
        return ApiResponse.ok();
    }

    /** 셀프 회원가입 — 비로그인 공개(SecurityConfig /api/auth/** permitAll). 이메일 인증코드 검증 후 MEMBER로 생성. */
    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        PasswordPolicy.validate(request.password()); // 복잡도 정책(인코딩 전 원문)
        authService.signup(request);
        return ApiResponse.ok();
    }

    /** 비밀번호 재설정 코드 발송(공개) — member_id로 조회해 등록 이메일로 발송. 계정 열거 방지 위해 항상 성공 응답. */
    @PostMapping("/sendResetCode")
    public ApiResponse<Void> sendResetCode(@RequestBody Map<String, String> body) {
        authService.sendResetCode(body.get("memberId"));
        return ApiResponse.ok();
    }

    /** 비밀번호 재설정(공개) — 인증코드 검증 후 새 비번 적용. */
    @PostMapping("/resetPassword")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PwResetRequest request) {
        PasswordPolicy.validate(request.newPw()); // 복잡도 정책(인코딩 전 원문)
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    /** 비밀번호 만료 연장("나중에") */
    @PostMapping("/pwExtend")
    public ApiResponse<Void> pwExtend() {
        authService.pwExtend();
        return ApiResponse.ok();
    }

    /** 본인 비밀번호 변경 */
    @PostMapping("/pwChange")
    public ApiResponse<Void> pwChange(@Valid @RequestBody PwChangeRequest request) {
        PasswordPolicy.validate(request.newPw()); // 복잡도 정책(인코딩 전 원문)
        authService.pwChange(request);
        return ApiResponse.ok();
    }

    /** 내 정보(memberId·nickname·typeCd) — 마이페이지 표시용 */
    @PostMapping("/me")
    public ApiResponse<Map<String, String>> me() {
        return ApiResponse.ok(authService.me());
    }

    /**
     * 회원 공개 프로필(닉네임·프로필사진·담은취미·주최모집·작성글) — 비로그인 공개. PII·로그인 ID 미노출.
     * 조회 키는 공개 식별자(handle).
     */
    @PostMapping("/memberProfile")
    public ApiResponse<Map<String, Object>> memberProfile(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.selectMemberProfile(body.get("handle")));
    }

    /** 본인 닉네임 변경 */
    @PostMapping("/nickname")
    public ApiResponse<Void> nickname(@Valid @RequestBody NicknameRequest request) {
        authService.changeNickname(request);
        return ApiResponse.ok();
    }

    /** 본인 프로필 사진 설정/해제 — 서버가 member_id 강제. body {fileId} 없으면 해제. */
    @PostMapping("/updateProfileImage")
    public ApiResponse<Void> updateProfileImage(@RequestBody Map<String, String> body) {
        authService.updateProfileImage(body.get("fileId"));
        return ApiResponse.ok();
    }

    /** 회원 탈퇴(셀프) — 현재 비밀번호 확인 필요. */
    @PostMapping("/withdraw")
    public ApiResponse<Void> withdraw(@RequestBody Map<String, String> body) {
        authService.withdraw(body.get("currentPw"));
        return ApiResponse.ok();
    }

    /** Access 만료 시 Refresh 토큰으로 재발급 */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    /** 로그아웃 — 서버에서 token_ver를 올려 현재 발급된 토큰을 즉시 무효화(인증 필요). */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }
}
