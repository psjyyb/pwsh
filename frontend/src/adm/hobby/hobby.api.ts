import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 취미 카탈로그 VO */
export interface Hobby {
  dbKey?: string // PK(hobby_id)
  hobbyNm?: string
  summary?: string
  intro?: string
  guide?: string
  difficultyCd?: string
  difficultyNm?: string
  equipment?: string
  estCost?: string
  bbsinfoId?: string
  bbsinfoNm?: string
  sortOrdr?: string
  thumbId?: string
  postCnt?: string
  useYn?: string
}

export const hobbyApi = {
  ...createCrudApi<Hobby>('/adm/hobby', 'Hobby'),
  /** 전체 취미(도감/드롭다운용) */
  listAll: () =>
    apiPost<ListResult<Hobby>>('/adm/hobby/selectHobbyList.do', { pageIndex: 1, size: 100 }).then((r) => r.list),
}
export const HOBBY_LIST_URL = hobbyApi.listUrl
