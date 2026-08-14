package com.pwsh.domain.bbs.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 게시글 VO (t_bbs). PK(bbs_id)는 BaseVO.dbKey. 작성자는 reg_id. */
@Data
@EqualsAndHashCode(callSuper = true)
public class BbsVO extends BaseVO {

    private String bbsinfoId;      // 소속 게시판(필터)
    private String title;
    private String context;
    private String searchSnippet;  // 통합검색 전용: 본문에서 찾았을 때 매칭 지점 주변 발췌(태그 제거된 평문)
    private String fileId;         // 썸네일(Phase2)
    private String pBbsId;
    private String bbsDepth;
    private String bbsOrdr;
    private String secretYn;
    private String bbsPw;
    private String goodCnt;
    private String badCnt;
    private String likedYn;   // 조회 표시: 내가 좋아요 눌렀는지(Y/N)
    private String viewerId;  // 좋아요 여부 판정용 현재 조회자(서버 세팅, 비로그인 null)
    private String viewCnt;
    private String noticeYn;
    private String noticeStartDt;
    private String noticeEndDt;
    private String bbsDt;
    // 조회 계산/표시용
    private String bbsinfoNm;      // 게시판명(t_bbsinfo 조인) — 내 글/인기글 등 여러 게시판 혼합 목록 표시용
    private String regNm;          // 작성자 표시명(닉네임, t_user 조인)
    private String regHandle;      // 작성자 공개 식별자(t_user.handle) — 프로필 링크용. 로그인 ID 대체
    private String mineYn;         // 'Y'=내가 쓴 글(서버 계산). 프론트 수정/삭제·신고노출 판정용
    private String regProfileFileId; // 작성자 프로필 사진(t_user.profile_file_id)
    private String commentCnt;     // 댓글 수
    private String noticeEff;      // 'Y'=공지 유효(기간내) → 목록 상단
    private String secretLocked;   // 'Y'=비밀글 잠금(작성자/관리자 아님·비번 불일치) → 내용 미반환
    private String viewUp;         // 'Y'=조회수 증가 대상(프론트가 브라우저 기록으로 중복 판단). 관리자 열람 등은 'N'

}
