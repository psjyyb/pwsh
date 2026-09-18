import { apiPost } from '../../api/http'

/** 환경설정 VO (단일 행) */
export interface Config {
  failCntLimit?: string
  failLockMins?: string
  passwordExpireDays?: string
  sessionExpireMins?: string
  delLogDays?: string
  dormantDays?: string // 미접속 휴면 전환일(0이면 전환 안 함)
  dormantNotifyDays?: string // 휴면 전환 며칠 전 안내메일(0이면 안 보냄)
  destroyDays?: string // 탈퇴 후 개인정보 보존일(0이면 즉시 파기)
  accIpYn?: string
  maintYn?: string // 점검(유지보수) 모드 — Y면 관리자 외 모든 요청이 503
  maintMessage?: string // 점검 안내 문구(사용자 화면에 노출)
  title?: string
  menuVersion?: string
  logoFileId?: string // 관리자 로고 이미지 file_id (없으면 프론트 기본 로고)
}

const BASE = '/adm/config'

export const configApi = {
  view: () => apiPost<Config>(`${BASE}/selectConfigView.do`, {}),
  update: (vo: Partial<Config>) => apiPost<void>(`${BASE}/updateConfig.do`, vo),
}
