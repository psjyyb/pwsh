import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'

/** 배치 작업(batch_job). 행은 코드에 구현된 작업과 1:1이라 등록·삭제가 없다 */
export interface BatchJob {
  rowId?: string // PK(batch_job_id)
  jobCd?: string
  name?: string
  description?: string
  cronExpr?: string // Spring cron 6자리(초 분 시 일 월 요일)
  autoYn?: string // 자동 실행 Y/N
  // 서버가 스케줄러를 보고 채우는 값
  nextRunDt?: string // 다음 예약 실행(자동 실행이 꺼져 있으면 없음)
  scheduledYn?: string
  // 마지막 실행
  lastStatusCd?: string // BATCH01 실행 중 / BATCH02 성공 / BATCH03 실패
  lastStatusName?: string
  lastManualYn?: string
  lastStartDt?: string
  lastEndDt?: string
  lastResultMsg?: string
}

/** 실행 이력(batch_log) */
export interface BatchLog {
  rowId?: string
  jobCd?: string
  jobName?: string
  manualYn?: string // Y=즉시 실행, N=예약 실행
  statusCd?: string
  statusName?: string
  resultMsg?: string
  startDt?: string
  endDt?: string
  regId?: string // 즉시 실행한 관리자(예약 실행은 system)
  regIp?: string
}

const BASE = '/adm/batch'
export const BATCH_LOG_LIST_URL = `${BASE}/selectBatchListLog.do`

export const batchApi = {
  list: () => apiPost<ListResult<BatchJob>>(`${BASE}/selectBatchList.do`, {}),
  /** 주기·자동 실행 변경 — 저장하면 재시작 없이 바로 다음 회차부터 반영된다 */
  update: (rowId: string, cronExpr: string, autoYn: string) =>
    apiPost<void>(`${BASE}/updateBatch.do`, { rowId, cronExpr, autoYn }),
  /** 즉시 실행 — 바로 응답하고 작업은 뒤에서 돈다(→ 실행 이력 ID) */
  run: (rowId: string) => apiPost<string>(`${BASE}/updateBatchRun.do`, { rowId }),
}
