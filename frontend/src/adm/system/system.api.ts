import client from '../../api/client'
import { apiPost } from '../../api/http'

/** 앱 상태 */
export interface SystemApp {
  version?: string
  cmsVersion?: string
  buildTime?: string
  profiles?: string
  javaVersion?: string
  uptime?: string
  heapUsedMb?: number
  heapMaxMb?: number
  mailEnabled?: string
}

/** 적용된 마이그레이션 한 줄 */
export interface Migration {
  version?: string
  description?: string
  success?: boolean
  executionTime?: number
  installedOn?: string
}

export interface SystemDb {
  size?: string
  version?: string
  migrations?: Migration[]
}

export interface SystemDisk {
  path?: string
  totalMb?: number
  freeMb?: number
  uploadMb?: number
  uploadFiles?: number
}

export interface SystemSchedule {
  name?: string
  cron?: string
}

export interface SystemCache {
  name?: string
  size?: number
  hit?: number
  miss?: number
  hitRate?: number
}

/** 실시간(SSE) 연결 현황 — 이 JVM에 붙은 것만. 인스턴스를 늘리면 합계가 아니다 */
export interface SystemRealtime {
  connections?: number
  members?: number
}

export interface SystemStatus {
  app: SystemApp
  db: SystemDb
  disk: SystemDisk
  schedules: SystemSchedule[]
  caches: SystemCache[]
  realtime: SystemRealtime
}

const BASE = '/adm/system'

export const systemApi = {
  status: () => apiPost<SystemStatus>(`${BASE}/selectSystemView.do`, {}),
  /** 캐시 전부 비우기 → 비운 캐시 수 */
  evictCache: () => apiPost<number>(`${BASE}/updateSystemCache.do`, {}),
  /**
   * 설정 스냅샷 내려받기.
   * GET이라 브라우저가 직접 열면 Authorization 헤더가 안 실린다 → axios로 blob을 받아 저장한다.
   */
  downloadBackup: async () => {
    const res = await client.get(`${BASE}/downloadBackup.do`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `config-backup-${new Date().toISOString().slice(0, 10)}.json`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  },
}
