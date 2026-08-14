import { apiPost } from './http'

/** 받은 후기 1건. */
export interface Review {
  rowId?: string
  recruitId?: string
  rating?: string
  content?: string
  regDt?: string
  regNm?: string
  regHandle?: string // 작성자 공개 식별자(프로필 링크용)
  regProfileFileId?: string
  recruitTitle?: string
}

/** 회원 신뢰지표(평균 별점·후기 수). 후기 없으면 avgRating=null, reviewCnt='0'. */
export interface ReviewStats { avgRating?: string; reviewCnt?: string }

/** 내가 후기를 쓸 수 있는 대상(종료된 모임에서 함께한 회원). */
export interface ReviewTarget {
  recruitId?: string
  recruitTitle?: string
  targetHandle?: string // 후기 대상 공개 식별자
  targetNm?: string
  writtenYn?: string
}

export const reviewApi = {
  /** 회원(handle)이 받은 후기(공개) */
  listByTarget: (targetHandle: string) => apiPost<Review[]>('/adm/review/selectReviewList.do', { targetHandle }),
  /** 회원(handle) 평균 별점·후기 수(공개) */
  stats: (targetHandle: string) => apiPost<ReviewStats>('/adm/review/selectReviewListStats.do', { targetHandle }),
  /** 내가 쓸 수 있는 후기 대상 목록(로그인) */
  myTargets: () => apiPost<ReviewTarget[]>('/adm/review/selectReviewListTargets.do', {}),
  /** 후기 등록(대상은 handle) */
  insert: (recruitId: string, targetHandle: string, rating: number, content?: string) =>
    apiPost<void>('/adm/review/insertReview.do', { recruitId, targetHandle, rating: String(rating), content: content ?? '' }),
  /** 후기 삭제(작성자·관리자) */
  remove: (rowId: string) => apiPost<void>('/adm/review/deleteReview.do', { rowId }),
}
