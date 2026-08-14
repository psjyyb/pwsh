import { useEffect, useState } from 'react'
import { message } from 'antd'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'

/**
 * 목록 화면 공통 훅 — 페이징·검색·로딩을 캡슐화.
 * 새 목록 화면은 컬럼과 검색 파라미터만 정의하면 됨.
 *
 *   const { rows, total, loading, page, pageSize, search, changePage, reload } =
 *     useList<Code>('/adm/code/selectCodeList.do')
 */
export function useList<T>(url: string, initialParams: Record<string, unknown> = {}) {
  const [rows, setRows] = useState<T[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [params, setParams] = useState<Record<string, unknown>>(initialParams)

  const load = async (p: number, sz: number, prm: Record<string, unknown>) => {
    setLoading(true)
    try {
      const res = await apiPost<ListResult<T>>(url, { pageNo: p, pageSize: sz, ...prm })
      setRows(res.list)
      setTotal(res.totalCount)
      setPage(p)
      setPageSize(sz)
      setParams(prm)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  // 최초 1회 로드
  useEffect(() => {
    load(1, 10, initialParams)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return {
    rows,
    total,
    loading,
    page,
    pageSize,
    /** 현재 조건으로 재조회 (등록/수정/삭제 후) */
    reload: () => load(page, pageSize, params),
    /** 검색조건 갱신 후 1페이지부터 조회 */
    search: (prm: Record<string, unknown>) => load(1, pageSize, { ...params, ...prm }),
    /** 페이지/사이즈 변경 */
    changePage: (p: number, sz: number) => load(p, sz, params),
  }
}
