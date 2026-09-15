import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 메일 템플릿 VO. subject·content의 `{{키}}`는 발송 시 치환된다(값은 HTML 이스케이프 후 삽입) */
export interface MailTemplate {
  rowId?: string // PK(mail_template_id)
  templateCd?: string
  name?: string
  subject?: string
  content?: string
  variables?: string
  useYn?: string
  updDt?: string
}

/** 치환 미리보기 결과. missing = 값이 없어 지워진 키(비어 있으면 정상) */
export interface MailPreview {
  subject: string
  content: string
  missing: string
}

export const mailTemplateApi = {
  ...createCrudApi<MailTemplate>('/adm/mailtemplate', 'MailTemplate'),
  /** 저장 전 치환 결과 확인(발송하지 않는다) — selectList{variant=Preview} */
  preview: (templateCd: string, vars: Record<string, string>) =>
    apiPost<MailPreview>('/adm/mailtemplate/selectMailTemplateListPreview.do', { templateCd, vars }),
}
export const MAIL_TEMPLATE_LIST_URL = mailTemplateApi.listUrl
