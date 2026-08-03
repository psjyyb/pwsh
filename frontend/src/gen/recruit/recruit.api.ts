import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'

/** 모집 VO. 주최자는 regId(표시명 regNm). */
export interface Recruit {
  dbKey?: string
  hobbyId?: string
  title?: string
  content?: string
  capacity?: string
  region?: string
  meetDt?: string
  statusCd?: string
  viewCnt?: string
  hobbyNm?: string // 취미명
  statusNm?: string // 모집상태명
  regId?: string // 주최자 ID
  regNm?: string // 주최자 닉네임
  regProfileFileId?: string // 주최자 프로필 사진 file_id
  regDt?: string
  applyCnt?: string // 활성 신청 수
  acceptedCnt?: string // 수락 수
}

/** 참여 신청 VO. */
export interface RecruitApply {
  dbKey?: string
  recruitId?: string
  userId?: string
  applyStatus?: string
  applyMemo?: string
  applyStatusNm?: string
  nickname?: string // 신청자 닉네임
  recruitTitle?: string
  regDt?: string
}

export interface RecruitListParams {
  hobbyId?: string
  statusCd?: string
  region?: string
  searchKeyword?: string
  pageIndex?: number
  size?: number
}

export const RECRUIT_LIST_URL = '/adm/recruit/selectRecruitList.do'

export const recruitApi = {
  list: (params: RecruitListParams) => apiPost<ListResult<Recruit>>(RECRUIT_LIST_URL, params),
  view: (dbKey: string, viewUp = false) =>
    apiPost<Recruit>('/adm/recruit/selectRecruitView.do', { dbKey, viewUp: viewUp ? 'Y' : 'N' }),
  insert: (vo: Partial<Recruit>) => apiPost<string>('/adm/recruit/insertRecruit.do', vo),
  update: (vo: Partial<Recruit>) => apiPost<void>('/adm/recruit/updateRecruit.do', vo),
  /** 모집상태 변경(마감 RECRUIT02 / 재개 RECRUIT01) */
  changeStatus: (dbKey: string, statusCd: string) =>
    apiPost<void>('/adm/recruit/updateRecruitStatus.do', { dbKey, statusCd }),
  remove: (dbKey: string) => apiPost<void>('/adm/recruit/deleteRecruit.do', { dbKey }),
}

export const applyApi = {
  /** 특정 모집의 신청자 목록(주최자·관리자) */
  listByRecruit: (recruitId: string) =>
    apiPost<RecruitApply[]>('/adm/recruitApply/selectRecruitApplyList.do', { recruitId }),
  /** 내 신청 내역 */
  mine: () => apiPost<RecruitApply[]>('/adm/recruitApply/selectRecruitApplyListMine.do', {}),
  /** 참여 신청 */
  apply: (recruitId: string, applyMemo?: string) =>
    apiPost<void>('/adm/recruitApply/insertRecruitApply.do', { recruitId, applyMemo }),
  /** 수락(APPLY02)/거절(APPLY03) — 주최자·관리자 */
  changeStatus: (dbKey: string, applyStatus: string) =>
    apiPost<void>('/adm/recruitApply/updateRecruitApply.do', { dbKey, applyStatus }),
  /** 신청 취소 — 본인 */
  cancel: (dbKey: string) => apiPost<void>('/adm/recruitApply/deleteRecruitApply.do', { dbKey }),
}
