import { apiPost } from '../../api/http'

/** 개인정보 접근 로그 VO (조회 전용). 복호화 조회가 일어난 요청 1건 = 1행 */
export interface Privacylog {
  rowId?: string // PK(privacy_log_id)
  memberId?: string // 조회한 사람(취급자)
  requestUri?: string // 수행 업무(요청 경로)
  sqlIds?: string // 개인정보를 읽은 매퍼 sql_id
  targetIds?: string // 처리한 정보주체(회원 ID)
  accessCnt?: string
  deviceType?: string
  userAgent?: string
  regDt?: string
  regIp?: string
}

export const PRIVACYLOG_LIST_URL = '/adm/privacylog/selectPrivacylogList.do'

export const privacylogApi = {
  view: (rowId: string) => apiPost<Privacylog>('/adm/privacylog/selectPrivacylogView.do', { rowId }),
}
