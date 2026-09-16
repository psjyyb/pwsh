import { apiPost } from '../../api/http'
import type { FormField } from '../form/form.api'

/** 폼 응답(제출 1회 = 1행) */
export interface FormAnswer {
  rowId?: string // PK(form_answer_id)
  formId?: string
  formTitle?: string
  memberId?: string // 비로그인 제출이면 없음
  statusCd?: string // ANSWER01 접수 / 02 처리중 / 03 완료 / 04 반려
  statusName?: string
  adminMemo?: string
  regDt?: string
  regIp?: string
  /** 문항ID → 답 */
  values?: Record<string, string>
}

/** 문항별 집계. 선택형이면 options에 선택지별 응답 수 */
export interface FormStat {
  fieldId: string
  label: string
  fieldCd: string
  answerCnt: number
  options?: { label: string; cnt: number }[]
}

export const FORM_ANSWER_LIST_URL = '/adm/formanswer/selectFormAnswerList.do'

export const formAnswerApi = {
  view: (rowId: string) => apiPost<FormAnswer>('/adm/formanswer/selectFormAnswerView.do', { rowId }),
  /** 사용자 제출 — 비로그인도 호출한다(폼이 허용하는 경우) */
  submit: (formId: string, values: Record<string, string>) =>
    apiPost<string>('/adm/formanswer/insertFormAnswer.do', { formId, values }),
  /** 처리상태·담당자 메모 변경 */
  changeStatus: (rowId: string, statusCd: string, adminMemo?: string) =>
    apiPost<void>('/adm/formanswer/updateFormAnswerStatus.do', { rowId, statusCd, adminMemo }),
  remove: (rowId: string) => apiPost<void>('/adm/formanswer/deleteFormAnswer.do', { rowId }),
  stats: (formId: string) => apiPost<FormStat[]>('/adm/formanswer/selectFormAnswerListStats.do', { formId }),
  /** 내려받기용 전체 응답 — 개인정보 문항이 있으면 이 조회가 접근기록에 남는다 */
  exportAll: (formId: string) =>
    apiPost<{ fields: FormField[]; list: FormAnswer[] }>(
      '/adm/formanswer/selectFormAnswerListExport.do', { formId },
    ),
}
