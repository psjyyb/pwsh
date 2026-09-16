package com.pwsh.domain.form.service;

import com.pwsh.common.BaseVO;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 폼 응답 VO (form_answer). PK(form_answer_id)는 BaseVO.rowId.
 *
 * <p>문항별 값은 {@code values}(문항ID → 답)로 주고받는다. 다중선택은 줄바꿈으로 이어 붙인다.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormAnswerVO extends BaseVO {

    private String formId;
    private String formTitle;
    /** 제출자(비로그인 제출이면 null) */
    private String memberId;
    /** ANSWER01 접수 / 02 처리중 / 03 완료 / 04 반려 */
    private String statusCd;
    private String statusName;
    private String adminMemo;

    /** 문항ID → 답. 제출 요청과 상세 응답 양쪽에 쓴다 */
    private Map<String, String> values;

    /**
     * 이 폼에 개인정보 문항이 있는지("Y"/"N") — 매퍼가 복호화 구문을 넣을지 결정한다.
     * 항상 복호화하면 개인정보가 하나도 없는 설문을 봐도 접근기록에 남아 기록이 무의미해진다.
     * 클라이언트 값이 아니라 <b>서비스가 폼 정의를 보고 세팅</b>한다.
     */
    private String hasPrivacy;
}
