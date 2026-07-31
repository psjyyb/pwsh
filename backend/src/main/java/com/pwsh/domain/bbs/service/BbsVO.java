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
    private String fileId;         // 썸네일(Phase2)
    private String pBbsId;
    private String bbsDepth;
    private String bbsOrdr;
    private String secretYn;
    private String bbsPw;
    private String goodCnt;
    private String badCnt;
    private String viewCnt;
    private String noticeYn;
    private String noticeStartDt;
    private String noticeEndDt;
    private String bbsDt;
    // 조회 계산/표시용
    private String regNm;          // 작성자 표시명(닉네임, t_user 조인). 없으면 프론트가 reg_id로 폴백
    private String commentCnt;     // 댓글 수
    private String noticeEff;      // 'Y'=공지 유효(기간내) → 목록 상단
    private String secretLocked;   // 'Y'=비밀글 잠금(작성자/관리자 아님·비번 불일치) → 내용 미반환
    private String viewUp;         // 'Y'=조회수 증가 대상(프론트가 브라우저 기록으로 중복 판단). 관리자 열람 등은 'N'

}
