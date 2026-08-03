import { apiPost } from './http'
import type { Hobby } from '../adm/hobby/hobby.api'
import type { Recruit } from '../gen/recruit/recruit.api'
import type { Bbs } from '../adm/bbs/bbs.api'

export interface SearchResult {
  hobbies: Hobby[]
  recruits: Recruit[]
  posts: Bbs[]
}

export const searchApi = {
  /** 취미·모집·게시글 통합 검색 */
  all: (searchKeyword: string) => apiPost<SearchResult>('/adm/search/selectSearchAll.do', { searchKeyword }),
}
