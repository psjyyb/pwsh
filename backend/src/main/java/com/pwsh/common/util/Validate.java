package com.pwsh.common.util;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;

/**
 * 컨트롤러 입력 검증 헬퍼. 프론트 검증에 더한 백엔드 방어선.
 * 위반 시 {@link BusinessException}(INVALID_INPUT). insert/update 시작부 guard clause로 사용한다.
 * (등록·수정이 같은 VO를 공유하므로, 각 컨트롤러가 그 요청에서 실제 필수인 필드만 골라 호출한다.)
 */
public final class Validate {

    private Validate() {}

    /** 필수값(공백 불가). 비어 있으면 "{label}은(는) 필수입니다." */
    public static void required(String value, String label) {
        if (StringUtil.isEmpty(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + "은(는) 필수입니다.");
        }
    }
}
