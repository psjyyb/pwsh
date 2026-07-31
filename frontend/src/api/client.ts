import axios from 'axios'
import { tokenStore } from '../auth/token'

/**
 * 공통 axios 인스턴스.
 * - baseURL '/api' (Vite dev 프록시 → 8080, 배포 시 리버스 프록시)
 * - 요청: JWT Access 토큰 자동 첨부
 * - 응답: 401이면 Refresh 토큰으로 1회 자동 재발급 후 재시도, 실패 시 로그인으로 이동
 */
const client = axios.create({
  baseURL: '/api',
})

client.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function toLogin() {
  tokenStore.clear()
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

// 동시 401이 각자 /auth/refresh를 쏘면(회전 토큰 시) 뒤 요청이 stale refresh로 실패 → 강제 로그아웃.
// 진행 중인 refresh 하나를 모듈 스코프에 공유해, 동시 401은 그 결과를 함께 기다린다.
let refreshInFlight: Promise<string> | null = null

function refreshAccess(refreshToken: string): Promise<string> {
  if (!refreshInFlight) {
    // 재발급은 raw axios로(인터셉터 재귀 방지)
    refreshInFlight = axios
      .post('/api/auth/refresh', { refreshToken })
      .then(({ data }) => {
        const newAccess = data?.data?.accessToken as string
        const newRefresh = data?.data?.refreshToken as string
        if (!newAccess) throw new Error('no token')
        tokenStore.set(newAccess, newRefresh)
        return newAccess
      })
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

client.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    const refreshToken = tokenStore.getRefresh()

    // 401 + refresh 보유 + 아직 재시도 안 함 → 공유 refresh로 재발급 후 원요청 재시도
    if (error.response?.status === 401 && refreshToken && original && !original._retry) {
      original._retry = true
      try {
        const newAccess = await refreshAccess(refreshToken)
        original.headers = original.headers ?? {}
        original.headers.Authorization = `Bearer ${newAccess}`
        return client(original)
      } catch {
        toLogin()
        return Promise.reject(error)
      }
    }

    if (error.response?.status === 401) {
      toLogin()
    }
    return Promise.reject(error)
  },
)

export default client
