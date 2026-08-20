package com.pwsh.domain.follow.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원 팔로우(member_follow). 단방향 — 내가 팔로우해도 상대가 나를 팔로우한 것은 아니다.
 * 클라이언트는 로그인 ID 대신 공개 식별자(handle)로만 상대를 지목한다.
 */
@Getter
@Setter
public class FollowVO extends BaseVO {

    private String memberId;        // 팔로우한 회원(주체) — 서버가 강제
    private String followeeId;    // 팔로우당한 회원(대상) — handle에서 변환

    private String followeeHandle; // 요청/응답의 대상 지목 키
    private String followeeName;     // 닉네임(목록 표시)
    private String followeeFileId; // 프로필 사진

    private String followedYn;     // 토글 결과: 'Y'=팔로우 중
    private String followerCnt;    // 대상의 팔로워 수(프로필 표시)
    private String followingCnt;   // 대상이 팔로우한 수
}
