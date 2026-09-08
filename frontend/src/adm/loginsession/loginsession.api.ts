import { apiPost } from '../../api/http'

/** 접속 세션 VO */
export interface LoginSession {
  rowId?: string // PK(login_session_id)
  memberId?: string
  memberName?: string
  deviceType?: string
  userAgent?: string
  loginDt?: string
  lastSeenDt?: string
  endDt?: string
  endReason?: string
  regIp?: string // 로그인 시점의 접속 IP(BaseVO 공통 audit 컬럼)
  activeYn?: string // 'Y'면 현재 접속 중
}

const BASE = '/adm/loginsession'

/**
 * 세션은 로그인으로만 생기고 로그아웃·강제종료로만 끝나므로 등록/삭제 API가 없다.
 * 표준 createCrudApi를 쓰지 않고 필요한 것만 노출한다.
 */
export const loginSessionApi = {
  listUrl: `${BASE}/selectLoginSessionList.do`,
  view: (rowId: string) => apiPost<LoginSession>(`${BASE}/selectLoginSessionView.do`, { rowId }),
  /** 강제 종료 — 세션을 닫고 대상의 토큰까지 무효화한다. */
  forceEnd: (rowId: string) => apiPost<void>(`${BASE}/updateLoginSessionForceEnd.do`, { rowId }),
}

export const LOGINSESSION_LIST_URL = loginSessionApi.listUrl
