import { apiPost } from '../../api/http'

/** 피드 항목 — feedType으로 게시글/모집을 구분한다(해당 없는 필드는 비어 있음). */
export interface FeedItem {
  feedType?: 'BBS' | 'RECRUIT'
  rowId?: string
  title?: string
  hobbyId?: string
  hobbyNm?: string
  regNm?: string
  regHandle?: string
  mineYn?: string
  regDt?: string
  // BBS
  bbsinfoId?: string
  commentCnt?: string
  goodCnt?: string
  // RECRUIT
  meetDt?: string
  areaNm?: string
  region?: string
  statusCd?: string
  statusNm?: string
  capacity?: string
  acceptedCnt?: string
}

/** 피드 응답 — 표준 목록 구조 + myHobbyCnt(빈 이유 구분용). */
export interface FeedResult {
  list: FeedItem[]
  totCnt: number
  myHobbyCnt: number
  page?: { currentPage: number; size: number; totalElements: number }
}

export const feedApi = {
  /** 내 취미 피드(로그인 필요) — feedFilter ''=전체 / BBS / RECRUIT */
  list: (feedFilter = '', pageIndex = 1, size = 20) =>
    apiPost<FeedResult>('/adm/feed/selectFeedList.do', { feedFilter, pageIndex, size }),
}
