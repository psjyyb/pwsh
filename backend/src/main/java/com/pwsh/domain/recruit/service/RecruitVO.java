package com.pwsh.domain.recruit.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 모집 VO (t_recruit). PK(recruit_id)는 BaseVO.dbKey. 주최자는 reg_id. */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecruitVO extends BaseVO {

    private String hobbyId;     // 취미(카테고리) = t_hobby.hobby_id
    private String title;       // 모임명
    private String content;     // 모집 설명
    private String capacity;    // 모집 인원
    private String region;      // 활동 지역
    private String meetDt;      // 모임 일정(YYYY-MM-DD)
    private String statusCd;    // 모집상태(RECRUIT00)
    private String viewCnt;
    // 조회 표시용(조인/계산)
    private String hobbyNm;     // 취미명(t_hobby 조인)
    private String statusNm;    // 모집상태명(t_code 조인)
    private String regNm;       // 주최자 닉네임(t_user 조인). 없으면 reg_id 폴백
    private String applyCnt;    // 활성 신청 수(대기+수락)
    private String acceptedCnt; // 수락 수
}
