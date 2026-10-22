/** 개인정보 파기 이력 VO (조회 전용). 자동 파기(배치)·관리자 즉시 파기 때 백엔드가 자동 기록 */
export interface DestroyLog {
  rowId?: string // PK(destroy_log_id)
  memberId?: string // 파기된 회원 ID
  destroyCd?: string // 파기 유형 코드(DESTROY00)
  destroyName?: string // 파기 유형 한글명(code 조인)
  withdrawDt?: string // 탈퇴 시각(즉시 파기면 없을 수 있음)
  actorId?: string // 처리자(자동이면 없음)
  regDt?: string // 파기 시각
  regIp?: string
}

export const DESTROYLOG_LIST_URL = '/adm/destroylog/selectDestroyLogList.do'
