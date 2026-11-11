import { apiPost } from '../../api/http'

/** 메일 발송 이력 VO (조회 전용, append-only) */
export interface MailLog {
  rowId?: string // PK(mail_log_id)
  templateCd?: string
  toEmail?: string
  subject?: string
  content?: string
  statusCd?: string // SUCCESS / FAIL / SKIP (code MAIL00)
  statusName?: string
  errorMsg?: string
  regId?: string
  regDt?: string
}

export const MAIL_LOG_LIST_URL = '/adm/maillog/selectMailLogList.do'

/** 발송 결과 — statusCd는 이력과 같은 값(SUCCESS/SKIP/FAIL), errorMsg는 미발송·실패 사유 */
export interface MailSendResult {
  sent: boolean
  statusCd: string
  errorMsg?: string
}

export const mailLogApi = {
  view: (rowId: string) => apiPost<MailLog>('/adm/maillog/selectMailLogView.do', { rowId }),
  /** 템플릿으로 1통 발송(관리자 확인용) → 결과·사유. 이력에도 그대로 남는다 */
  send: (templateCd: string, toEmail: string, vars: Record<string, string>) =>
    apiPost<MailSendResult>('/adm/maillog/insertMailLog.do', { templateCd, toEmail, vars }),
}
