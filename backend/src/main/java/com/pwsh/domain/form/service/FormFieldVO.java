package com.pwsh.domain.form.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 폼 문항 VO (form_field). PK(form_field_id)는 BaseVO.rowId.
 *
 * <p>{@code privacyYn='Y'}인 문항의 답은 암호화해 저장한다 — 그래야 조회가 개인정보
 * 접근기록에 남는다(탐지 기준이 "복호화해서 읽었는가"다).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormFieldVO extends BaseVO {

    private String formId;
    private String label;
    /** FIELD01 단답 / 02 장문 / 03 단일선택 / 04 다중선택 / 05 드롭다운 / 06 날짜 / 07 숫자 / 08 이메일 */
    private String fieldCd;
    private String fieldName;
    /** 선택지(줄바꿈 구분). 선택형 문항만 사용 */
    private String options;
    private String placeholder;
    private String requiredYn;
    /** 개인정보 문항 — 답을 암호화 저장 */
    private String privacyYn;
    private String sortNo;
}
