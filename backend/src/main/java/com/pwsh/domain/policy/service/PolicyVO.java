package com.pwsh.domain.policy.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 약관/정책 VO (policy). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PolicyVO extends BaseVO {

    // PK(policy_id)는 BaseVO.rowId로 통일
    private String title;
    private String content;
    private String typeCd;
    private String typeCdName; // 약관유형명(code 조인, 목록 표시용)
    private String reqYn;
    private String sortNo;
    // 동의 이력(member_policy) insert 전용. BaseVO 파라미터여야 AuditInterceptor가 audit을 채운다.
    private String memberId;
}
