import { apiPost } from '../../api/http'

/** 환경설정 VO (단일 행) */
export interface Config {
  failCntLimit?: string
  failLockMins?: string
  passwordExpireDays?: string
  sessionExpireMins?: string
  delLogDays?: string
  accIpYn?: string
  title?: string
  menuVersion?: string
  logoFileId?: string // 관리자 로고 이미지 file_id (없으면 프론트 기본 로고)
}

const BASE = '/adm/config'

export const configApi = {
  view: () => apiPost<Config>(`${BASE}/selectConfigView.do`, {}),
  update: (vo: Partial<Config>) => apiPost<void>(`${BASE}/updateConfig.do`, vo),
}
