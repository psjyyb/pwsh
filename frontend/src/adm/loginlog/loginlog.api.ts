/** 로그인 이력 VO (조회 전용). 성공·실패 모두 백엔드가 로그인 시도마다 자동 기록 */
export interface LoginLog {
  rowId?: string // PK(login_log_id)
  loginId?: string // 입력한 아이디 그대로(없는 아이디도 남는다)
  resultCd?: string // 결과 코드(LOGIN00)
  resultName?: string // 결과 한글명(code 조인)
  failMessage?: string // 실패 시 사용자에게 나간 안내문
  deviceType?: string // desktop/mobile/tablet
  userAgent?: string
  regDt?: string
  regIp?: string
}

export const LOGINLOG_LIST_URL = '/adm/loginlog/selectLoginLogList.do'
