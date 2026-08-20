package com.pwsh.domain.auth.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 이메일 인증코드(email_verification). 가입 이메일 인증·비밀번호 재설정 공용.
 * target = 식별키(SIGNUP=이메일, RESET=member_id), purpose = SIGNUP/RESET.
 */
@Getter
@Setter
public class EmailVerificationVO extends BaseVO {
    private String target;
    private String purpose;
    private String code;
    private String expireDt;
    /** 실패 시도 횟수(무차별 대입 차단용). */
    private String attemptCnt;
    /** 유효기간(분). insert 시 expire_dt = NOW() + ttlMin 분 계산용. */
    private String ttlMin;
}
