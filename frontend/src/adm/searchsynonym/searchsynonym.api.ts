import { createCrudApi } from '../../api/crudApi'

/** 검색 동의어 묶음 VO(CMS 1.2.39) */
export interface SearchSynonym {
  rowId?: string // PK(synonym_id)
  /** 묶음의 말들 — 서버가 정리해 쉼표로 이어 저장한다(보낼 땐 쉼표·줄바꿈 어느 쪽으로 나눠도 된다) */
  words?: string
  useYn?: string
  updDt?: string
}

export const searchSynonymApi = createCrudApi<SearchSynonym>('/adm/searchsynonym', 'SearchSynonym')
export const SEARCH_SYNONYM_LIST_URL = searchSynonymApi.listUrl
