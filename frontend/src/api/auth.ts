import { apiPost } from './http'
import { tokenStore } from '../auth/token'

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  pwExpired?: boolean | null // 비밀번호 만료(알림용, 강제 아님)
  pwDaysLeft?: number | null // 만료까지 남은 일수
}

/** 로그인 → JWT(Access/Refresh) 발급 */
export function login(userId: string, userPw: string): Promise<TokenResponse> {
  return apiPost<TokenResponse>('/auth/login', { userId, userPw })
}

export interface SignupParams {
  userId: string
  userPw: string
  pwConfirm: string
  nickname: string
  email?: string
}

/** 셀프 회원가입 — 성공 시 MEMBER 권한으로 계정 생성(자동 로그인은 아님). */
export function signup(params: SignupParams): Promise<void> {
  return apiPost<void>('/auth/signup', params)
}

export interface MeInfo { userId?: string; nickname?: string; memCd?: string; profileFileId?: string }
/** 내 정보(마이페이지) */
export function me(): Promise<MeInfo> {
  return apiPost<MeInfo>('/auth/me', {})
}

/** 본인 프로필 사진 설정/해제 — fileId 없으면 해제 */
export function updateProfileImage(fileId?: string): Promise<void> {
  return apiPost<void>('/auth/updateProfileImage', { fileId: fileId ?? null })
}

/** 비밀번호 만료 연장("나중에") — 본인 pw_expire_dt 재형성 */
export function extendPw(): Promise<void> {
  return apiPost<void>('/auth/pwExtend', {})
}

/** 본인 비밀번호 변경 (현재 비번 검증) */
export function changePw(currentPw: string, newPw: string): Promise<void> {
  return apiPost<void>('/auth/pwChange', { currentPw, newPw })
}

/**
 * 로그아웃 — 서버에서 token_ver를 올려 발급된 토큰(access·refresh)을 즉시 무효화한 뒤 로컬 토큰 제거.
 * 서버 호출이 실패(이미 무효/네트워크 오류)해도 로컬 정리는 진행한다. 화면 이동은 호출부 담당.
 */
export async function logout(): Promise<void> {
  try {
    await apiPost<void>('/auth/logout', {})
  } catch {
    /* 이미 무효화됐거나 네트워크 실패 — 로컬 정리로 진행 */
  }
  tokenStore.clear()
}
