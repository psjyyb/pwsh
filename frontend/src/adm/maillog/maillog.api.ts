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

export const mailLogApi = {
  view: (rowId: string) => apiPost<MailLog>('/adm/maillog/selectMailLogView.do', { rowId }),
  /** 템플릿으로 1통 발송(관리자 확인용). 결과(성공 여부)는 이력에 그대로 남는다 */
  send: (templateCd: string, toEmail: string, vars: Record<string, string>) =>
    apiPost<boolean>('/adm/maillog/insertMailLog.do', { templateCd, toEmail, vars }),
}
