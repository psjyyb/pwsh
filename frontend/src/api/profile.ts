import { apiPost } from './http'

export interface ProfileHobby { hobbyId?: string; hobbyNm?: string; levelNm?: string }
export interface ProfilePost {
  dbKey?: string; bbsinfoId?: string; bbsinfoNm?: string; title?: string
  commentCnt?: string; goodCnt?: string; viewCnt?: string; regDt?: string
}
export interface ProfileRecruit {
  dbKey?: string; hobbyNm?: string; title?: string; statusCd?: string; statusNm?: string
  acceptedCnt?: string; capacity?: string; regDt?: string
}

/** 참석 통계(신뢰지표) — 주최자가 기록한 모임만 집계. */
export interface AttendStats { attendedCnt?: string; absentCnt?: string; noshowCnt?: string }

/** 회원 공개 프로필(PII·로그인 ID 미포함). 회원 지목은 공개 식별자(handle). */
export interface UserProfile {
  handle?: string
  attend?: AttendStats
  nickname?: string
  profileFileId?: string
  hobbies?: ProfileHobby[]
  posts?: ProfilePost[]
  recruits?: ProfileRecruit[]
}

export function getUserProfile(handle: string): Promise<UserProfile> {
  return apiPost<UserProfile>('/auth/userProfile', { handle })
}
