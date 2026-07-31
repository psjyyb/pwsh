package com.pwsh.domain.auth.service;

/**
 * 로그인/재발급 응답.
 * pwExpired/pwDaysLeft는 로그인 시 비밀번호 만료 알림용(강제 아님). 재발급 시에는 null/false.
 */
public record TokenResponse(String accessToken, String refreshToken, Boolean pwExpired, Integer pwDaysLeft) {

    /** 재발급 등 만료정보 불필요할 때 */
    public TokenResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, null, null);
    }
}
