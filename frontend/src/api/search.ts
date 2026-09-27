import { apiPost } from './http'
import type { Hobby } from '../adm/hobby/hobby.api'
import type { Recruit } from '../gen/recruit/recruit.api'
import type { Post } from '../adm/post/post.api'

/** 안내페이지 검색 결과 한 줄(백엔드 PageVO) */
export interface SearchPageHit {
  rowId?: string
  title?: string
  regDt?: string
  searchSnippet?: string
}

/**
 * 통합검색 응답.
 *
 * ★ 목록은 유형별 최대 10건까지만 온다. 화면에 찍는 건수는 배열 길이가 아니라 *Count를 써야 한다 —
 * 길이를 쓰면 결과가 23건이어도 "10"으로 보여 사용자를 속인다.
 */
export interface SearchResult {
  hobbies: Hobby[]
  hobbyCount: number
  recruits: Recruit[]
  recruitCount: number
  posts: Post[]
  postCount: number
  pages: SearchPageHit[]
  pageCount: number
}

/** 유형별로 내려오는 최대 건수(서버 SearchService.PREVIEW_SIZE와 같은 값) */
export const SEARCH_PREVIEW_SIZE = 10

export const searchApi = {
  /** 취미·모집·게시글·안내페이지 통합 검색 */
  all: (filterKeyword: string) => apiPost<SearchResult>('/adm/search/selectSearchAll.do', { filterKeyword }),
}
