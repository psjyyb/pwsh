import { apiPost } from './http'

/** 차단한 회원 1건. */
export interface Block {
  rowId?: string
  blockedHandle?: string // 대상 공개 식별자
  blockedName?: string
  blockedFileId?: string
  regDt?: string
}

export const blockApi = {
  /** 차단 토글(대상은 handle) → {blockedYn} */
  toggle: (blockedHandle: string) =>
    apiPost<{ blockedYn?: string }>('/adm/block/updateBlockToggle.do', { blockedHandle }),
  /** 내가 차단한 회원 목록 */
  list: () => apiPost<Block[]>('/adm/block/selectBlockList.do', {}),
  /** 내가 차단한 회원 handle 목록(콘텐츠 숨김 판정용) */
  ids: () => apiPost<string[]>('/adm/block/selectBlockListIds.do', {}),
  /** 특정 회원(handle) 차단 여부('Y'/'N') */
  check: (blockedHandle: string) => apiPost<string>('/adm/block/selectBlockListCheck.do', { blockedHandle }),
}
