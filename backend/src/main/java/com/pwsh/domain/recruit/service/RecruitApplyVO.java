package com.pwsh.domain.recruit.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 모집 참여 신청 VO (t_recruit_apply). PK(apply_id)는 BaseVO.dbKey. 신청자는 userId(=reg_id). */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecruitApplyVO extends BaseVO {

    private String recruitId;    // 대상 모집
    private String userId;       // 신청자(서버에서 세팅)
    private String applyStatus;  // 신청상태(APPLY00)
    private String applyMemo;    // 신청 메모
    // 조회 표시용(조인)
    private String applyStatusNm; // 신청상태명(t_code 조인)
    private String nickname;      // 신청자 닉네임(t_user 조인). 없으면 userId 폴백
    private String recruitTitle;  // 모집 모임명(t_recruit 조인)
}
