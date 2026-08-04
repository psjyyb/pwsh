import { apiPost } from './http'

export interface Report {
  dbKey?: string
  targetType?: string // BBS/COMMENT/RECRUIT
  targetId?: string
  reasonCd?: string // 신고 사유 분류(t_code REPORT00)
  reasonNm?: string // 분류명(조회)
  reason?: string
  status?: string // PENDING/RESOLVED/DISMISSED
  targetTitle?: string
  linkUrl?: string // 대상으로 이동할 경로(관리자 확인용)
  regNm?: string
  regDt?: string
}

export const reportApi = {
  /** 신고 등록(로그인 회원) — reasonCd=사유 분류(REPORT00), reason=상세 */
  report: (targetType: 'BBS' | 'COMMENT' | 'RECRUIT', targetId: string, reason: string, reasonCd?: string) =>
    apiPost<void>('/adm/report/insertReport.do', { targetType, targetId, reason, reasonCd: reasonCd ?? '' }),
  /** 신고 목록(관리자) */
  list: (status?: string) => apiPost<Report[]>('/adm/report/selectReportList.do', { status: status ?? '' }),
  /** 신고 처리(관리자) */
  updateStatus: (dbKey: string, status: string) =>
    apiPost<void>('/adm/report/updateReportStatus.do', { dbKey, status }),
}
