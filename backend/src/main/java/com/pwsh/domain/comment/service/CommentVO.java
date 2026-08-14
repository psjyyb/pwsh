package com.pwsh.domain.comment.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 댓글 VO (t_comment). PK(comment_id)는 BaseVO.rowId. 작성자는 reg_id. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentVO extends BaseVO {

    private String bbsId;   // 소속 게시글
    private String pCommentId; // 부모 댓글(0/빈값=최상위, 값=대댓글)
    private String context;
    private String goodCnt;
    private String badCnt;
    private String likedYn;   // 조회 표시: 내가 좋아요 눌렀는지(Y/N)
    private String viewerId;  // 좋아요 여부 판정용 현재 조회자(서버 세팅, 비로그인 null)
    private String regNm;   // 작성자 표시명(닉네임, t_user 조인)
    private String regHandle; // 작성자 공개 식별자(t_user.handle) — 프로필 링크용. 로그인 ID 대체
    private String mineYn;    // 'Y'=내가 쓴 댓글(서버 계산). 수정/삭제·신고노출 판정용
    private String regProfileFileId; // 작성자 프로필 사진(t_user.profile_file_id)
}
