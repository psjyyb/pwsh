import client from '../../api/client'
import { apiPost } from '../../api/http'
import type { MailSendResult } from '../maillog/maillog.api'

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

/** 배치 주기 요약 — 원본·변경·실행은 배치 작업 관리 화면 */
export interface SystemSchedule {
  name?: string
  cron?: string
  autoYn?: string
  lastStatusName?: string
  lastStartDt?: string
}

/** 메일 설정 요약. ★ 비밀번호는 서버가 내려주지 않는다 */
export interface SystemMail {
  enabled?: string
  host?: string
  port?: number
  senderSet?: string
  sender?: string // 앞 두 글자만 보이게 가린 발신 계정
  logRetentionDays?: number
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
  mail?: SystemMail
}

const BASE = '/adm/system'

export const systemApi = {
  status: () => apiPost<SystemStatus>(`${BASE}/selectSystemView.do`, {}),
  /** 캐시 전부 비우기 → 비운 캐시 수 */
  evictCache: () => apiPost<number>(`${BASE}/updateSystemCache.do`, {}),
  /** 메일 설정 점검 — 템플릿 없이 고정 문구 한 통 → 결과·사유(이력에도 남는다) */
  mailCheck: (toEmail: string) => apiPost<MailSendResult>(`${BASE}/updateSystemMailCheck.do`, { toEmail }),
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
