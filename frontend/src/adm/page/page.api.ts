import { createCrudApi } from '../../api/crudApi'

/** 페이지(단일 콘텐츠) VO */
export interface Page {
  rowId?: string // PK(page_id)
  title: string
  context: string
  useYn?: string
}

export const pageApi = createCrudApi<Page>('/adm/page', 'Page')
export const PAGE_LIST_URL = pageApi.listUrl
