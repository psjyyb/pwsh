const ACCESS_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

/** JWT 토큰 저장소 (localStorage). Access + Refresh 관리. */
export const tokenStore = {
  get: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  set: (accessToken: string, refreshToken?: string) => {
    localStorage.setItem(ACCESS_KEY, accessToken)
    if (refreshToken !== undefined) {
      localStorage.setItem(REFRESH_KEY, refreshToken)
    }
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

/** JWT payload 클레임 (subject=memberId, typeCd=회원유형) */
export interface JwtClaims {
  sub?: string
  typeCd?: string
  exp?: number
}

/** base64url 디코드 (JWT payload용) */
function base64UrlDecode(str: string): string {
  const b64 = str.replace(/-/g, '+').replace(/_/g, '/')
  const pad = b64.length % 4 ? '='.repeat(4 - (b64.length % 4)) : ''
  return atob(b64 + pad)
}

/** Access Token을 디코드해 클레임 반환 (없거나 형식오류면 null) */
export function getClaims(): JwtClaims | null {
  const token = tokenStore.get()
  if (!token) return null
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    return JSON.parse(base64UrlDecode(parts[1])) as JwtClaims
  } catch {
    return null
  }
}

/** 관리자(회원유형 MEM02) 여부 — /adm 영역 접근 기준 */
export function isAdmin(): boolean {
  return getClaims()?.typeCd === 'MEM02'
}
