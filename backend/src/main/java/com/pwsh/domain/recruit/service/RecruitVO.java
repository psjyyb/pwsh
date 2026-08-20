package com.pwsh.domain.recruit.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 모집 VO (recruit). PK(recruit_id)는 BaseVO.rowId. 주최자는 reg_id. */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecruitVO extends BaseVO {

    private String hobbyId;     // 취미(카테고리) = hobby.hobby_id
    private String title;       // 모임명
    private String content;     // 모집 설명
    private String capacity;    // 모집 인원
    private String region;      // 활동 지역
    private String placeName;     // 만날 장소명(지도에서 선택)
    private String addr;         // 장소 주소(지도에서 선택)
    private String lat;          // 위도(지도 마커). 장소 미지정이면 빈 값
    private String lng;          // 경도(지도 마커)
    private String meetDt;      // 모임 일정(YYYY-MM-DD)
    private String statusCd;    // 모집상태(RECRUIT00)
    private String viewCnt;
    // 조회 표시용(조인/계산)
    private String hobbyName;     // 취미명(hobby 조인)
    private String statusName;    // 모집상태명(code 조인)
    private String areaCd;      // 지역 시/도 표준코드(code AREA00) — 목록 필터 기준
    private String areaName;      // 지역명(code 조인, 표시용)
    private String regName;       // 주최자 닉네임(member 조인)
    private String regHandle;   // 주최자 공개 식별자(member.handle) — 프로필 링크용. 로그인 ID 대체
    private String mineYn;      // 'Y'=내가 연 모집(서버 계산). 수정/삭제·신청자목록 판정용
    private String viewerId;    // mine_yn 판정용 현재 조회자(서버 세팅, 비로그인 null)
    private String regProfileFileId; // 주최자 프로필 사진(member.profile_file_id)
    private String applyCnt;    // 활성 신청 수(대기+수락)
    private String acceptedCnt; // 수락 수
    private String viewUp;      // 조회수 증가 플래그('Y'일 때만 증가 — 새로고침 중복증가 방지)
}
