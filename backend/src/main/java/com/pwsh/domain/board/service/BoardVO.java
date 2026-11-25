package com.pwsh.domain.board.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 게시판 정의 VO (board). PK(board_id)는 BaseVO.rowId. */
@Data
@EqualsAndHashCode(callSuper = true)
public class BoardVO extends BaseVO {

    private String boardName;
    private String typeCd;      // 게시판 유형(code BOARD00)
    private String boardCdName;    // 유형명(조인, 목록 표시용)
    private String description;
    private String listCnt;        // 목록당 게시글 수
    private String fileYn;         // 첨부 사용
    private String fileCntLimit;        // 첨부 개수 제한(기본 5)
    private String fileSizeLimitMb;       // 첨부 용량 제한 MB(기본 10)
    private String noticeYn;       // 공지 사용
    private String newCnt;         // NEW 표시 일수
    private String approvalYn;     // 게시 승인 사용(CMS 1.2.41) — Y면 회원 글은 관리자 승인 전까지 작성자·관리자만 본다
    private String rssYn;          // RSS 제공 여부(CMS 1.2.43, 서버 계산 — 사이트 주소가 있고 비로그인이 읽는 게시판·취미 게시판). 화면이 RSS 링크를 띄울지 정한다
}
