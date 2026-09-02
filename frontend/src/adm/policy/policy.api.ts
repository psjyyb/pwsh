import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 약관/정책 VO */
export interface Policy {
  rowId?: string // PK(policy_id)
  title?: string
  content?: string
  typeCd: string
  typeCdName?: string // 약관유형명(목록 표시, 조회 전용)
  reqYn?: string
  sortNo?: string
  useYn?: string
}

export const policyApi = {
  ...createCrudApi<Policy>('/adm/policy', 'Policy'),
  /** 순서 변경(같은 약관유형 내 인접 약관과 교환) */
  moveSort: (rowId: string, direction: 'UP' | 'DOWN') =>
    apiPost<void>('/adm/policy/updatePolicySort.do', { rowId, direction }),
  /** 공개 목록(가입 동의 항목·푸터) — 비로그인 허용, 본문 없음. 필수동의가 먼저 온다. */
  publicList: () =>
    apiPost<{ list: Policy[] }>('/adm/policy/selectPolicyListPublic.do', {}).then((r) => r.list),
  /** 약관 본문(공개) — 동의 전 내용 확인·푸터에서 열람 */
  publicView: (rowId: string) =>
    apiPost<Policy>('/adm/policy/selectPolicyView.do', { rowId }),
}
export const POLICY_LIST_URL = policyApi.listUrl
