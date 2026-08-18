import { apiPost } from './http'

/** 회원 팔로우(단방향). 상대 지목은 공개 식별자(handle)로만 한다. */
export interface Follow {
  rowId?: string
  followeeHandle?: string
  followeeNm?: string
  followeeFileId?: string
  followedYn?: string
  followerCnt?: string
  followingCnt?: string
  regDt?: string
}

export const followApi = {
  /** 내가 팔로우한 회원 */
  following: () => apiPost<Follow[]>('/adm/follow/selectFollowList.do', {}),
  /** 나를 팔로우한 회원 */
  followers: () => apiPost<Follow[]>('/adm/follow/selectFollowListFollowers.do', {}),
  /** 내가 이 회원을 팔로우했는지 ('Y'/'N') */
  check: (followeeHandle: string) =>
    apiPost<string>('/adm/follow/selectFollowListCheck.do', { followeeHandle }),
  /** 대상 회원의 팔로워/팔로잉 수 */
  counts: (followeeHandle: string) =>
    apiPost<Follow>('/adm/follow/selectFollowListCounts.do', { followeeHandle }),
  /** 팔로우 토글 → { followedYn } */
  toggle: (followeeHandle: string) =>
    apiPost<Follow>('/adm/follow/updateFollowToggle.do', { followeeHandle }),
}
