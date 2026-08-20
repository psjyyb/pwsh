import { apiPost } from './http'
import type { ListResult } from './http'

export interface Report {
  rowId?: string
  targetType?: string // POST/COMMENT/RECRUIT
  targetId?: string
  reasonCd?: string // 신고 사유 분류(code REPORT00)
  reasonName?: string // 분류명(조회)
  reason?: string
  statusType?: string // PENDING/RESOLVED/DISMISSED
  targetTitle?: string
  linkUrl?: string // 대상으로 이동할 경로(관리자 확인용)
  regName?: string
  regDt?: string
}

export const reportApi = {
  /** 신고 등록(로그인 회원) — reasonCd=사유 분류(REPORT00), reason=상세 */
  report: (targetType: 'POST' | 'COMMENT' | 'RECRUIT' | 'CHAT', targetId: string, reason: string, reasonCd?: string) =>
    apiPost<void>('/adm/report/insertReport.do', { targetType, targetId, reason, reasonCd: reasonCd ?? '' }),
  /** 신고 목록(관리자, 서버 페이징) — statusType 빈 값이면 전체 */
  list: (statusType?: string, pageNo = 1, pageSize = 20) =>
    apiPost<ListResult<Report>>('/adm/report/selectReportList.do', { statusType: statusType ?? '', pageNo, pageSize }),
  /** 신고 처리(관리자) */
  updateStatus: (rowId: string, statusType: string) =>
    apiPost<void>('/adm/report/updateReportStatus.do', { rowId, statusType }),
}
