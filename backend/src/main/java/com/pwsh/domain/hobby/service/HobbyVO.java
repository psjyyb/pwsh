package com.pwsh.domain.hobby.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 취미 카탈로그 VO (hobby). PK(hobby_id)는 BaseVO.rowId. */
@Data
@EqualsAndHashCode(callSuper = true)
public class HobbyVO extends BaseVO {

    private String hobbyName;       // 취미명
    private String summary;       // 한줄 소개
    private String intro;         // 소개 본문(HTML)
    private String guide;         // 입문 가이드(HTML)
    private String difficultyCd;  // 난이도(HOBBYLV00)
    private String equipment;     // 필요 장비
    private String estimatedCost;       // 대략 비용
    private String boardId;     // 연결 게시판(board)
    private String sortNo;      // 노출 순서
    // 조회 표시용(조인/계산)
    private String difficultyName;  // 난이도명(code)
    private String thumbId;       // 대표이미지 file_id(file_ref, loc HOBBY)
    private String boardName;     // 연결 게시판명
    private String postCnt;       // 연결 게시판 글 수
    private String memberCnt;      // 이 취미를 담은 회원 수(member_hobby, 인기도)
}
