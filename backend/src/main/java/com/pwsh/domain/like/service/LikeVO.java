package com.pwsh.domain.like.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 추천/비추천 VO (content_like). 요청은 대상과 구분만 받고, 누른 사람(memberId)은 서버가 세팅한다.
 * 응답에는 누른 뒤의 내 상태(likedYn·dislikedYn)와 대상의 숫자(goodCnt·badCnt)를 담는다.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LikeVO extends BaseVO {

    private String memberId;     // 누른 회원(서버 세팅 — 클라이언트 값을 쓰면 남의 이름으로 표를 던질 수 있다)
    private String targetType;   // POST / COMMENT
    private String targetId;     // post_id 또는 comment_id
    private String likeType;     // GOOD(추천) / BAD(비추천). 비면 GOOD
    private String prevType;     // 전환 시 이전 구분(매퍼 내부용)

    // ===== 응답 =====
    private String likedYn;      // 'Y'=지금 내가 추천한 상태
    private String dislikedYn;   // 'Y'=지금 내가 비추천한 상태
    private String goodCnt;
    private String badCnt;
}
