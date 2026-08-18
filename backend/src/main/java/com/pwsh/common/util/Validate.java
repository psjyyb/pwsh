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

    /**
     * 숫자 PK/외래키 검증(빈 값은 통과 — 선택 조건에도 쓰기 위함).
     * VO 필드가 전부 String이라 매퍼에서 ::integer로 캐스팅되는데, 사용자가 URL로 직접 넣은
     * 문자열이 그대로 내려가면 SQL 캐스트 에러(500)가 난다. 그걸 400으로 되돌린다.
     */
    public static void numeric(String value, String label) {
        if (!StringUtil.isEmpty(value) && !value.matches("\\d+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + "이(가) 올바르지 않습니다.");
        }
    }
}
