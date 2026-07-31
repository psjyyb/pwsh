package com.pwsh.common.util;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;

/**
 * 비밀번호 복잡도 정책 — 8~20자, 영문·숫자·특수문자 각 1자 이상, 공백 불가.
 * 위반 시 {@link BusinessException}(WEAK_PASSWORD). 신규 등록·비밀번호 변경 경로에서 인코딩 전에 호출한다.
 * (기존 계정/시드는 이 경로를 거치지 않으므로 영향 없음.)
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    private static final int MIN = 8;
    private static final int MAX = 64; // 긴 암호문구(passphrase) 허용

    public static void validate(String pw) {
        if (pw == null || pw.length() < MIN || pw.length() > MAX) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD);
        }
        boolean letter = false, digit = false, special = false;
        for (int i = 0; i < pw.length(); i++) {
            char c = pw.charAt(i);
            if (Character.isWhitespace(c)) {
                throw new BusinessException(ErrorCode.WEAK_PASSWORD); // 공백 불가
            } else if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                special = true;
            }
        }
        if (!(letter && digit && special)) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD);
        }
    }
}
