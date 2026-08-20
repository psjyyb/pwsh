import { apiPost } from './http'
import type { Hobby } from '../adm/hobby/hobby.api'
import type { Recruit } from '../gen/recruit/recruit.api'
import type { Post } from '../adm/post/post.api'

export interface SearchResult {
  hobbies: Hobby[]
  recruits: Recruit[]
  posts: Post[]
}

export const searchApi = {
  /** 취미·모집·게시글 통합 검색 */
  all: (filterKeyword: string) => apiPost<SearchResult>('/adm/search/selectSearchAll.do', { filterKeyword }),
}
