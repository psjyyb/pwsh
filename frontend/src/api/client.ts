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

/** 점검 안내 문구 전달용 키 — 리다이렉트로 화면이 갈리므로 상태가 아니라 sessionStorage로 넘긴다. */
export const MAINT_MESSAGE_KEY = 'maintMessage'

/** 점검 모드(503) — 서버가 준 안내 문구를 들고 점검 화면으로 보낸다. 관리자는 503을 받지 않는다. */
function toMaintenance(message?: string) {
  if (message) {
    sessionStorage.setItem(MAINT_MESSAGE_KEY, message)
  }
  if (window.location.pathname !== '/maintenance') {
    window.location.assign('/maintenance')
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

    // 점검 모드 — 어떤 화면에서 났든 안내 화면으로 통일(개별 화면이 "조회 실패"만 띄우면 이유를 알 수 없다)
    if (error.response?.status === 503 && error.response?.data?.error?.code === 'C503') {
      toMaintenance(error.response.data.error.message)
      return Promise.reject(error)
    }

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
