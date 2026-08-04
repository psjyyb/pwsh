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
    private String areaCd;      // 지역 시/도 표준코드(t_code AREA00) — 목록 필터 기준
    private String areaNm;      // 지역명(t_code 조인, 표시용)
    private String regNm;       // 주최자 닉네임(t_user 조인)
    private String regHandle;   // 주최자 공개 식별자(t_user.handle) — 프로필 링크용. 로그인 ID 대체
    private String mineYn;      // 'Y'=내가 연 모집(서버 계산). 수정/삭제·신청자목록 판정용
    private String viewerId;    // mine_yn 판정용 현재 조회자(서버 세팅, 비로그인 null)
    private String regProfileFileId; // 주최자 프로필 사진(t_user.profile_file_id)
    private String applyCnt;    // 활성 신청 수(대기+수락)
    private String acceptedCnt; // 수락 수
    private String viewUp;      // 조회수 증가 플래그('Y'일 때만 증가 — 새로고침 중복증가 방지)
}
