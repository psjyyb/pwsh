package com.pwsh.domain.recruit.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 모집 참여 신청 VO (recruit_apply). PK(apply_id)는 BaseVO.rowId. 신청자는 memberId(=reg_id). */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecruitApplyVO extends BaseVO {

    private String recruitId;    // 대상 모집
    private String memberId;       // 신청자(서버에서 세팅)
    private String applyCd;  // 신청상태(APPLY00)
    private String applyMemo;    // 신청 메모
    // 조회 표시용(조인)
    /** 대기 순번(대기 상태일 때만, 신청순 1부터). 수락·거절 건은 null */
    private String waitNo;

    private String attendCd;      // 참석 결과(code ATTEND00). NULL=미기록
    private String attendName;      // 참석 결과명(code 조인)
    private String memberHandle;    // 신청자 공개 식별자(프로필 링크용)
    private String applyName; // 신청상태명(code 조인)
    private String nickname;      // 신청자 닉네임(member 조인)
    private String recruitTitle;  // 모집 모임명(recruit 조인)
    private String meetDt;        // 모임 일정(recruit 조인) — '내 모임 일정'용
    private String region;        // 모임 지역(recruit 조인)
    private String recruitStatusCd; // 모집 상태(recruit 조인)
}
