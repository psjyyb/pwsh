package com.pwsh.domain.postapproval.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 게시 승인(CMS 1.2.41) — 승인 화면이 보는 게시글 한 줄. 표는 post 그대로다(PK post_id = BaseVO.rowId).
 * 관리자 화면이라 작성자 로그인 ID(regId)를 함께 싣는다.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostApprovalVO extends BaseVO {

    private String boardId;        // 소속 게시판 — 목록 필터로도 받는다
    private String boardName;
    private String title;
    private String content;        // 상세에서만
    private String regName;        // 작성자 닉네임
    private String replyYn;        // 'Y'=답글
    private String approvalCd;     // code APPROVAL00 — 목록 필터·처리 값(02 승인 / 03 반려)
    private String approvalName;
    private String approvalMemo;   // 반려 사유
    private String approvalId;     // 처리한 관리자(서비스가 세팅)
    private String approvalDt;
    private String resubmitYn;     // 'Y'=처리된 뒤 작성자가 고쳐 다시 올린 대기 글
    private String keywordLike;    // 제목 찾기 — LIKE 이스케이프본(원문은 filterKeyword). 서비스가 채운다
}
