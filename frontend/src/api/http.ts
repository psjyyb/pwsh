import axios from 'axios'
import client from './client'

/** 백엔드 표준 응답 봉투 (ApiResponse) */
export interface ApiResponse<T> {
  success: boolean
  data: T
  error: { code: string; message: string } | null
}

/** 목록 응답 표준 구조 (selectXxxList.do) */
export interface ListResult<T> {
  list: T[]
  totalCount: number
  page: {
    currentPage: number
    pageSize: number
    totalElements: number
    totalPages: number
  }
}

/**
 * 표준 API 호출. 모든 도메인 액션 URL은 POST + JSON.
 * ApiResponse 봉투를 벗겨 data만 반환하고, 실패 시 메시지로 throw.
 */
export async function apiPost<T>(url: string, body?: unknown): Promise<T> {
  try {
    const res = await client.post<ApiResponse<T>>(url, body ?? {})
    if (!res.data.success) {
      throw new Error(res.data.error?.message ?? '요청에 실패했습니다.')
    }
    return res.data.data
  } catch (e) {
    if (axios.isAxiosError(e)) {
      const apiErr = (e.response?.data as ApiResponse<unknown> | undefined)?.error
      throw new Error(apiErr?.message ?? e.message)
    }
    throw e
  }
}
