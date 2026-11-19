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
  /** 네 유형 모두 0건일 때만 오는 오타 교정 제안(CMS 1.2.39) — 없으면 빠지거나 null */
  suggest?: string | null
}

/** 유형별로 내려오는 최대 건수(서버 SearchService.PREVIEW_SIZE와 같은 값) */
export const SEARCH_PREVIEW_SIZE = 10

export const searchApi = {
  /** 취미·모집·게시글·안내페이지 통합 검색 */
  all: (filterKeyword: string) => apiPost<SearchResult>('/adm/search/selectSearchAll.do', { filterKeyword }),
  /** 인기 검색어(CMS 1.2.38) — 최근 7일 많이 찾은 말(금칙어·숨긴 말 제외). 검색어 문자열만 온다 */
  popular: () => apiPost<string[]>('/adm/search/selectSearchListPopular.do', {}),
  /** 자동완성(CMS 1.2.39) — 자주 검색되고 결과가 있던 말 중 입력이 든 말(앞이 맞는 말 먼저). 주소·본문은 CMS와 같다 */
  suggest: (keyword: string) => apiPost<string[]>('/adm/search/selectSearchListSuggest.do', { keyword }),
}
