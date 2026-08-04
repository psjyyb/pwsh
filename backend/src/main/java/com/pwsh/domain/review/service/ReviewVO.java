package com.pwsh.domain.review.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/** 모임 후기·평점(t_review). 작성자는 reg_id(서버 세팅), 대상은 targetId. */
@Getter
@Setter
public class ReviewVO extends BaseVO {

    private String recruitId;  // 대상 모임
    private String targetId;   // 후기를 받는 회원
    private String rating;     // 별점 1~5
    private String content;    // 후기 내용(선택)

    // 조회 표시용
    private String targetNm;      // 대상 닉네임
    private String regNm;         // 작성자 닉네임
    private String regProfileFileId; // 작성자 프로필 사진
    private String recruitTitle;  // 모임명
    private String avgRating;     // 평균 별점(집계)
    private String reviewCnt;     // 후기 수(집계)
    private String writtenYn;     // 내가 이 대상에게 이미 썼는지(Y/N)
}
