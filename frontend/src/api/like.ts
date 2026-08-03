import { apiPost } from './http'

/** 좋아요 토글 결과 */
export interface LikeResult {
  likedYn?: string // 토글 후 내 좋아요 상태(Y/N)
  goodCnt?: string // 토글 후 총 좋아요 수
}

export const likeApi = {
  /** 게시글/댓글 좋아요 토글 */
  toggle: (targetType: 'BBS' | 'COMMENT', targetId: string) =>
    apiPost<LikeResult>('/adm/like/toggleLike.do', { targetType, targetId }),
}
