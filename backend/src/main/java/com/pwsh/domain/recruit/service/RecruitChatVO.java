package com.pwsh.domain.recruit.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 모임 단체 대화 한 줄(recruit_chat). 주최자 + 수락된 참여자만 읽고 쓴다.
 * 응답에는 로그인 ID를 담지 않는다 — 작성자 지목은 handle, 본인 판정은 mineYn(서버 계산).
 */
@Getter
@Setter
public class RecruitChatVO extends BaseVO {

    private String recruitId;
    private String content;

    // 조회 표시용
    private String regName;              // 작성자 닉네임
    private String regHandle;          // 작성자 공개 식별자(프로필 링크)
    private String regProfileFileId;   // 작성자 프로필 사진
    private String mineYn;             // 'Y'=내가 쓴 말풍선
    private String hostYn;             // 'Y'=주최자가 쓴 말(공지처럼 구분해 보여준다)
}
