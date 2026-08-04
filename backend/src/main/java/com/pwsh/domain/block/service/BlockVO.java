package com.pwsh.domain.block.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/** 회원 차단(t_user_block). user_id=차단 주체(서버 강제), blocked_id=대상. */
@Getter
@Setter
public class BlockVO extends BaseVO {

    private String userId;
    private String blockedId;      // 서버 내부 로그인 ID(응답에는 넣지 않음)
    private String blockedHandle;  // 대상 공개 식별자(handle) — 클라이언트가 쓰는 키

    // 조회 표시용
    private String blockedNm;      // 대상 닉네임
    private String blockedFileId;  // 대상 프로필 사진
    private String blockedYn;      // 토글 결과(Y=차단중)
}
