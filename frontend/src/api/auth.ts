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
  email: string
  code: string // 이메일 인증코드(6자리)
}

/** 셀프 회원가입 — 이메일 인증코드 검증 후 MEMBER 권한으로 계정 생성(자동 로그인은 아님). */
export function signup(params: SignupParams): Promise<void> {
  return apiPost<void>('/auth/signup', params)
}

/** 가입 이메일 인증코드 발송 — 입력 이메일로 6자리 코드 전송. */
export function sendSignupCode(email: string): Promise<void> {
  return apiPost<void>('/auth/sendSignupCode', { email })
}

/** 비밀번호 재설정 코드 발송 — 아이디에 등록된 이메일로 코드 전송(계정 열거 방지: 항상 성공 응답). */
export function sendResetCode(userId: string): Promise<void> {
  return apiPost<void>('/auth/sendResetCode', { userId })
}

/** 비밀번호 재설정 — 인증코드 검증 후 새 비밀번호 적용. */
export function resetPassword(params: {
  userId: string; code: string; newPw: string; pwConfirm: string
}): Promise<void> {
  return apiPost<void>('/auth/resetPassword', params)
}

/** 내 정보. handle=내 공개 식별자(프로필 링크·본인 판정용), userId=로그인 ID(본인 화면 표시용) */
export interface MeInfo { userId?: string; nickname?: string; memCd?: string; profileFileId?: string; handle?: string }
/** 내 정보(마이페이지) */
export function me(): Promise<MeInfo> {
  return apiPost<MeInfo>('/auth/me', {})
}

/** 본인 프로필 사진 설정/해제 — fileId 없으면 해제 */
export function updateProfileImage(fileId?: string): Promise<void> {
  return apiPost<void>('/auth/updateProfileImage', { fileId: fileId ?? null })
}

/** 회원 탈퇴(셀프) — 현재 비밀번호 확인 필요. 성공 시 계정 비활성 + 세션 무효화. */
export function withdraw(currentPw: string): Promise<void> {
  return apiPost<void>('/auth/withdraw', { currentPw })
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
