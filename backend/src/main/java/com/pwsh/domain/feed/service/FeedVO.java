package com.pwsh.domain.feed.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 내 취미 피드 항목 — 게시글(POST)과 모집(RECRUIT)을 한 타임라인으로 합친 조회 전용 VO.
 * 전용 테이블 없이 member_hobby 기준으로 post·recruit를 UNION ALL 집계한다.
 */
@Getter
@Setter
public class FeedVO extends BaseVO {

    /** 항목 유형: POST(게시글) / RECRUIT(모집) */
    private String feedType;

    // 공통
    private String title;
    private String hobbyId;
    private String hobbyName;
    private String regName;      // 작성자 닉네임
    private String regHandle;  // 작성자 공개 식별자(프로필 링크)
    private String mineYn;     // 'Y'=내가 쓴 것(서버 계산)

    // POST 전용
    private String boardId;  // 게시판 ID(링크 조립용)
    private String commentCnt;
    private String goodCnt;

    // RECRUIT 전용
    private String meetDt;
    private String areaName;
    private String region;
    private String statusCd;
    private String statusName;
    private String capacity;
    private String acceptedCnt;

    /** 피드에 오른 이유: HOBBY(담은 취미) / FOLLOW(팔로우한 회원) — 화면에서 출처 표시 */
    private String feedSrc;

    /** 피드 필터: ''=전체 / POST / RECRUIT */
    private String feedFilter;

    /** 피드 주인 = 현재 로그인 사용자(서비스가 강제 주입, 클라이언트 값 무시) */
    private String viewerId;
}
