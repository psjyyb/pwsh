import { apiPost } from './http'

/** 북마크 목록 항목(대상이 살아있는 것만). */
export interface Bookmark {
  rowId?: string
  targetType?: string // POST/RECRUIT
  targetId?: string
  title?: string
  subName?: string      // 게시판명(POST) / 취미명(RECRUIT)
  boardId?: string  // POST 이동용
  statusName?: string   // RECRUIT 상태명
  regDt?: string
}

export const bookmarkApi = {
  /** 북마크 토글 → {markedYn} */
  toggle: (targetType: 'POST' | 'RECRUIT', targetId: string) =>
    apiPost<{ markedYn?: string }>('/adm/bookmark/updateBookmarkToggle.do', { targetType, targetId }),
  /** 내 북마크 목록 */
  list: (targetType: 'POST' | 'RECRUIT') =>
    apiPost<Bookmark[]>('/adm/bookmark/selectBookmarkList.do', { targetType }),
  /** 내가 북마크한 대상 id 목록(목록 화면 표시용) */
  ids: (targetType: 'POST' | 'RECRUIT') =>
    apiPost<string[]>('/adm/bookmark/selectBookmarkListIds.do', { targetType }),
}
