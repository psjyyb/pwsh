import { apiPost } from './http'

/** 북마크 목록 항목(대상이 살아있는 것만). */
export interface Bookmark {
  dbKey?: string
  targetType?: string // BBS/RECRUIT
  targetId?: string
  title?: string
  subNm?: string      // 게시판명(BBS) / 취미명(RECRUIT)
  bbsinfoId?: string  // BBS 이동용
  statusNm?: string   // RECRUIT 상태명
  regDt?: string
}

export const bookmarkApi = {
  /** 북마크 토글 → {markedYn} */
  toggle: (targetType: 'BBS' | 'RECRUIT', targetId: string) =>
    apiPost<{ markedYn?: string }>('/adm/bookmark/updateBookmarkToggle.do', { targetType, targetId }),
  /** 내 북마크 목록 */
  list: (targetType: 'BBS' | 'RECRUIT') =>
    apiPost<Bookmark[]>('/adm/bookmark/selectBookmarkList.do', { targetType }),
  /** 내가 북마크한 대상 id 목록(목록 화면 표시용) */
  ids: (targetType: 'BBS' | 'RECRUIT') =>
    apiPost<string[]>('/adm/bookmark/selectBookmarkListIds.do', { targetType }),
}
