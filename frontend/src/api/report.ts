import { apiPost } from './http'

export interface Report {
  dbKey?: string
  targetType?: string // BBS/COMMENT/RECRUIT
  targetId?: string
  reason?: string
  status?: string // PENDING/RESOLVED/DISMISSED
  targetTitle?: string
  regNm?: string
  regDt?: string
}

export const reportApi = {
  /** 신고 등록(로그인 회원) */
  report: (targetType: 'BBS' | 'COMMENT' | 'RECRUIT', targetId: string, reason: string) =>
    apiPost<void>('/adm/report/insertReport.do', { targetType, targetId, reason }),
  /** 신고 목록(관리자) */
  list: (status?: string) => apiPost<Report[]>('/adm/report/selectReportList.do', { status: status ?? '' }),
  /** 신고 처리(관리자) */
  updateStatus: (dbKey: string, status: string) =>
    apiPost<void>('/adm/report/updateReportStatus.do', { dbKey, status }),
}
