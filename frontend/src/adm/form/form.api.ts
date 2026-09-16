import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 폼 문항. privacyYn='Y'면 답을 암호화 저장하고, 조회가 개인정보 접근기록에 남는다 */
export interface FormField {
  rowId?: string // PK(form_field_id). 새 문항은 비어 있다
  formId?: string
  label?: string
  fieldCd?: string // FIELD01 단답 … FIELD08 이메일
  fieldName?: string
  options?: string // 선택지(줄바꿈 구분)
  placeholder?: string
  requiredYn?: string
  privacyYn?: string
  sortNo?: string
}

/** 폼(신청·민원·설문 공용) */
export interface Form {
  rowId?: string // PK(form_id) — 메뉴 conn_id로 연결
  title?: string
  description?: string
  typeCd?: string // FORM01 신청 / FORM02 민원 / FORM03 설문
  typeName?: string
  startDt?: string
  endDt?: string
  loginYn?: string
  multiYn?: string
  doneMessage?: string
  answerCnt?: string
  useYn?: string
  fields?: FormField[]
}

/** 선택지를 쓰는 문항 유형 — 화면에서 선택지 입력칸을 보일지 정한다 */
export const CHOICE_FIELDS = ['FIELD03', 'FIELD04', 'FIELD05']

export const formApi = {
  ...createCrudApi<Form>('/adm/form', 'Form'),
  /** 메뉴 연결(폼) 피커용 */
  combo: () => apiPost<Form[]>('/adm/form/selectFormListCombo.do', {}),
}
export const FORM_LIST_URL = formApi.listUrl
