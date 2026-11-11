package com.pwsh.domain.auth.service;

/**
 * 로그인/재발급 응답.
 * pwExpired/pwDaysLeft는 로그인 시 비밀번호 만료 알림용(강제 아님). 재발급 시에는 null/false.
 * otpKey가 있으면 관리자 2차 인증 단계다 — 토큰 없이 확인 키만 나가고, 화면은 코드 입력으로 넘어간다
 * (otpMailSent=false면 코드 메일이 나가지 않았다: 메일 꺼짐·발송 실패, 메일 발송이력에서 사유 확인).
 */
public record TokenResponse(String accessToken, String refreshToken, Boolean pwExpired, Integer pwDaysLeft,
                            String otpKey, Boolean otpMailSent) {

    /** 재발급 등 만료정보 불필요할 때 */
    public TokenResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, null, null, null, null);
    }

    public TokenResponse(String accessToken, String refreshToken, Boolean pwExpired, Integer pwDaysLeft) {
        this(accessToken, refreshToken, pwExpired, pwDaysLeft, null, null);
    }

    /** 2차 인증 단계 — 토큰은 싣지 않는다 */
    public static TokenResponse otp(String otpKey, boolean mailSent) {
        return new TokenResponse(null, null, null, null, otpKey, mailSent);
    }
}
