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
}
