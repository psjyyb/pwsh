/** 이벤트(행위) 로그 VO (조회 전용). 로그인/등록/수정/삭제를 백엔드가 자동 기록 */
export interface Eventlog {
  dbKey?: string // PK(event_log_id)
  eventType?: string // 행위 코드(EVENT00): LOGIN/INSERT/UPDATE/DELETE
  eventTypeNm?: string // 행위 한글명(t_code 조인)
  userId?: string // 수행자
  targetTable?: string // 대상 테이블(예: t_user)
  targetId?: string // 대상 행 PK
  deviceType?: string // desktop/mobile/tablet
  userAgent?: string
  regDt?: string
  regIp?: string
}

export const EVENTLOG_LIST_URL = '/adm/eventlog/selectEventlogList.do'
