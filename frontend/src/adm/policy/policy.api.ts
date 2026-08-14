import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 약관/정책 VO */
export interface Policy {
  rowId?: string // PK(policy_id)
  title?: string
  content?: string
  typeCd: string
  typeCdNm?: string // 약관유형명(목록 표시, 조회 전용)
  reqYn?: string
  sortNo?: string
  useYn?: string
}

export const policyApi = {
  ...createCrudApi<Policy>('/adm/policy', 'Policy'),
  /** 순서 변경(같은 약관유형 내 인접 약관과 교환) */
  moveSort: (rowId: string, direction: 'UP' | 'DOWN') =>
    apiPost<void>('/adm/policy/updatePolicySort.do', { rowId, direction }),
}
export const POLICY_LIST_URL = policyApi.listUrl
