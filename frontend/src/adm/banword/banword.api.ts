import { createCrudApi } from '../../api/crudApi'

/** 금칙어 VO */
export interface Banword {
  rowId?: string // PK(banword_id)
  word?: string
  useYn?: string
}

export const banwordApi = createCrudApi<Banword>('/adm/banword', 'Banword')
export const BANWORD_LIST_URL = banwordApi.listUrl
