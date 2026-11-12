package com.pwsh.domain.auth.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 이메일 인증코드(email_verification). 가입 이메일 인증·비밀번호 재설정 공용. 몇 분짜리 임시 행이라 이력 컬럼이 없다.
 * target = 식별키(SIGNUP=이메일(소문자), RESET=member_id), purpose = SIGNUP/RESET.
 */
@Getter
@Setter
public class EmailVerificationVO extends BaseVO {
    private String target;
    private String purpose;
    /** 6자리 코드의 BCrypt 해시(CMS 1.2.35 — 평문 저장 안 함) */
    private String codeHash;
    /** 실패 시도 횟수(무차별 대입 차단용). */
    private String attemptCnt;
    /** 유효기간(분). insert 시 expire_dt = NOW() + ttlMin 분 계산용. */
    private String ttlMin;
    /** 재발송 제한(초) — 이 안에 같은 대상으로 발급한 행이 있으면 거절 */
    private String cooldownSec;
}
