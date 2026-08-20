package com.pwsh.domain.like.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 좋아요 VO (content_like). member_id는 서버 세팅. 응답엔 likedYn/goodCnt를 담아 반환. */
@Data
@EqualsAndHashCode(callSuper = true)
public class LikeVO extends BaseVO {

    private String memberId;      // 좋아요 누른 회원(서버 세팅)
    private String targetType;  // POST / COMMENT
    private String targetId;    // 대상 PK(post_id / comment_id)
    // 응답 표시용
    private String likedYn;     // 토글 후 내가 좋아요 상태인지
    private String goodCnt;     // 토글 후 총 좋아요 수
}
