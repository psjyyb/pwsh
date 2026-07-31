package com.pwsh.domain.comment.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 댓글 VO (t_comment). PK(comment_id)는 BaseVO.dbKey. 작성자는 reg_id. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentVO extends BaseVO {

    private String bbsId;   // 소속 게시글
    private String context;
    private String goodCnt;
    private String badCnt;
    private String regNm;   // 작성자 표시명(닉네임, t_user 조인). 없으면 프론트가 reg_id로 폴백
}
