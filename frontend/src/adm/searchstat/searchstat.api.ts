import { apiPost } from '../../api/http'

/** 검색 통계 한 줄 — 기간 합계(날짜·검색어별 집계, 누가 검색했는지는 없다) */
export interface SearchStat {
  keyword?: string
  searchCnt?: string
  zeroCnt?: string // 그중 결과가 0건이었던 횟수
  lastDate?: string
  hiddenYn?: string // 인기 검색어에서 숨김
  banYn?: string // 금칙어가 들어 있음(인기 검색어에 원래 안 나온다)
}

/** 일별 합계 한 줄(검색이 없던 날도 0으로 온다) */
export interface SearchStatDaily {
  statDate?: string
  searchCnt?: string
  zeroCnt?: string
  keywordCnt?: string // 그날 검색된 서로 다른 검색어 수
}

const BASE = '/adm/searchstat'
/** 검색어 순위(useList용) — 파라미터 days(기간)·zeroOnly(Y=결과 없던 검색어만)·filterKeyword */
export const SEARCH_STAT_LIST_URL = `${BASE}/selectSearchStatList.do`

export const searchStatApi = {
  daily: (days: string) => apiPost<{ list: SearchStatDaily[] }>(`${BASE}/selectSearchStatListDaily.do`, { days }),
  /** 인기 검색어에서 숨김(Y)·해제(N) — 검색어는 서버가 공백·대소문자를 정리해 비교한다 */
  hide: (keyword: string, hideYn: 'Y' | 'N') => apiPost<void>(`${BASE}/updateSearchStatHide.do`, { keyword, hideYn }),
}
