import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'

/** 모집 VO. 주최자는 regId(표시명 regNm). */
export interface Recruit {
  dbKey?: string
  hobbyId?: string
  title?: string
  content?: string
  capacity?: string
  areaCd?: string // 지역 시/도 표준코드(AREA00) — 필터 기준
  areaNm?: string // 지역명(표시용)
  region?: string // 상세 지역(자유입력: 구/동/장소)
  meetDt?: string
  statusCd?: string
  viewCnt?: string
  hobbyNm?: string // 취미명
  statusNm?: string // 모집상태명
  regId?: string // 주최자 로그인 ID — 관리자 화면 전용(사용자(GEN) 응답에는 내려오지 않음)
  regNm?: string // 주최자 닉네임
  regHandle?: string // 주최자 공개 식별자 — 프로필 링크용(GEN)
  mineYn?: string // 'Y'=내가 연 모집(서버 계산) — 수정/삭제·신청자목록 판정(GEN)
  regProfileFileId?: string // 주최자 프로필 사진 file_id
  regDt?: string
  applyCnt?: string // 활성 신청 수
  acceptedCnt?: string // 수락 수
}

/** 참여 신청 VO. */
export interface RecruitApply {
  dbKey?: string
  recruitId?: string
  userId?: string // 신청자 로그인 ID — 주최자·관리자용 신청자 목록에서만 내려옴(내 신청 내역엔 없음)
  applyStatus?: string
  applyMemo?: string
  applyStatusNm?: string
  attendCd?: string   // 참석 결과(ATTEND00). 미기록이면 없음
  attendNm?: string   // 참석 결과명
  userHandle?: string // 신청자 공개 식별자(프로필 링크)
  nickname?: string // 신청자 닉네임
  recruitTitle?: string
  meetDt?: string          // 모임 일정('내 모임 일정'용)
  region?: string          // 모임 지역
  recruitStatusCd?: string // 모집 상태
  regDt?: string
}

export interface RecruitListParams {
  hobbyId?: string
  statusCd?: string
  areaCd?: string  // 시/도 필터
  region?: string  // 상세 지역 부분일치
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
  /** 참석 결과 기록(주최자·관리자, 모임 종료 후). attendCd 빈 값이면 미기록으로 되돌림 */
  setAttend: (dbKey: string, attendCd: string) =>
    apiPost<void>('/adm/recruitApply/updateRecruitApplyAttend.do', { dbKey, attendCd }),
  /** 신청 취소 — 본인 */
  cancel: (dbKey: string) => apiPost<void>('/adm/recruitApply/deleteRecruitApply.do', { dbKey }),
}
