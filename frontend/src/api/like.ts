import { apiPost } from './http'

/** 추천/비추천 결과 — 누른 뒤의 내 상태와 대상의 숫자 */
export interface LikeResult {
  likedYn?: string    // 'Y'=지금 내가 추천한 상태
  dislikedYn?: string // 'Y'=지금 내가 비추천한 상태
  goodCnt?: string
  badCnt?: string
}

export type LikeTarget = 'POST' | 'COMMENT'
export type LikeType = 'GOOD' | 'BAD'

/** 같은 쪽을 다시 누르면 취소, 반대쪽을 누르면 전환(서버가 판정). 로그인 필요. */
export const likeApi = {
  vote: (targetType: LikeTarget, targetId: string, likeType: LikeType) =>
    apiPost<LikeResult>('/adm/like/updateLike.do', { targetType, targetId, likeType }),
}
