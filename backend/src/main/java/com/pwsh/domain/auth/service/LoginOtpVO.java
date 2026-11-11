package com.pwsh.domain.auth.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 관리자 2차 인증 코드 VO (login_otp). PK(login_otp_id)는 BaseVO.rowId. 몇 분짜리 임시 행이라 이력 컬럼이 없다. */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginOtpVO extends BaseVO {

    private String otpKey;      // 화면이 들고 다니는 확인 키(추측할 수 없는 난수)
    private String memberId;
    private String codeHash;    // 6자리 코드의 BCrypt 해시
    private String failCnt;     // 이 코드로 틀린 횟수
    private String expireMins;  // 등록 시 유효 시간(분)
    private String maxFail;     // 틀린 횟수 상한(이만큼 틀리면 폐기)
    private String expiredYn;   // 조회 전용 — 유효 시간이 지났는지
}
