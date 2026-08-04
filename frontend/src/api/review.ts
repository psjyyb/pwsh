import { apiPost } from './http'

/** 받은 후기 1건. */
export interface Review {
  dbKey?: string
  recruitId?: string
  targetId?: string
  rating?: string
  content?: string
  regDt?: string
  regNm?: string
  regProfileFileId?: string
  recruitTitle?: string
}

/** 회원 신뢰지표(평균 별점·후기 수). 후기 없으면 avgRating=null, reviewCnt='0'. */
export interface ReviewStats { avgRating?: string; reviewCnt?: string }

/** 내가 후기를 쓸 수 있는 대상(종료된 모임에서 함께한 회원). */
export interface ReviewTarget {
  recruitId?: string
  recruitTitle?: string
  targetId?: string
  targetNm?: string
  writtenYn?: string
}

export const reviewApi = {
  /** 회원이 받은 후기(공개) */
  listByTarget: (targetId: string) => apiPost<Review[]>('/adm/review/selectReviewList.do', { targetId }),
  /** 회원 평균 별점·후기 수(공개) */
  stats: (targetId: string) => apiPost<ReviewStats>('/adm/review/selectReviewListStats.do', { targetId }),
  /** 내가 쓸 수 있는 후기 대상 목록(로그인) */
  myTargets: () => apiPost<ReviewTarget[]>('/adm/review/selectReviewListTargets.do', {}),
  /** 후기 등록 */
  insert: (recruitId: string, targetId: string, rating: number, content?: string) =>
    apiPost<void>('/adm/review/insertReview.do', { recruitId, targetId, rating: String(rating), content: content ?? '' }),
  /** 후기 삭제(작성자·관리자) */
  remove: (dbKey: string) => apiPost<void>('/adm/review/deleteReview.do', { dbKey }),
}
