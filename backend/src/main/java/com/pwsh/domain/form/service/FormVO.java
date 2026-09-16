package com.pwsh.domain.form.service;

import com.pwsh.common.BaseVO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 폼 VO (form). PK(form_id)는 BaseVO.rowId.
 *
 * <p>신청·민원·설문을 {@code typeCd}로만 구분하는 한 엔진이다. 사용자 노출은 메뉴
 * 연결유형 MENU05(폼) + conn_id=form_id.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormVO extends BaseVO {

    private String title;
    private String description;
    /** FORM01 신청 / FORM02 민원 / FORM03 설문 */
    private String typeCd;
    private String typeName;
    private String startDt;
    private String endDt;
    /** 로그인 필요 여부 */
    private String loginYn;
    /** 같은 사람의 중복 제출 허용(N이면 1인 1회) */
    private String multiYn;
    private String doneMessage;
    /** 응답 수(목록 표시용) */
    private String answerCnt;

    /** 문항 목록. 조회 시 함께 내려주고, 저장 시 한 번에 받는다(화면 한 번 저장 = 요청 1건) */
    private List<FormFieldVO> fields;
}
