import { apiPost } from './http'

export interface ProfileHobby { hobbyId?: string; hobbyName?: string; levelName?: string }
export interface ProfilePost {
  rowId?: string; boardId?: string; boardName?: string; title?: string
  commentCnt?: string; goodCnt?: string; viewCnt?: string; regDt?: string
}
export interface ProfileRecruit {
  rowId?: string; hobbyName?: string; title?: string; statusCd?: string; statusName?: string
  acceptedCnt?: string; capacity?: string; regDt?: string
}

/** 참석 통계(신뢰지표) — 주최자가 기록한 모임만 집계. */
export interface AttendStats { attendedCnt?: string; absentCnt?: string; noshowCnt?: string }

/** 회원 공개 프로필(PII·로그인 ID 미포함). 회원 지목은 공개 식별자(handle). */
export interface MemberProfile {
  handle?: string
  attend?: AttendStats
  nickname?: string
  profileFileId?: string
  hobbies?: ProfileHobby[]
  posts?: ProfilePost[]
  recruits?: ProfileRecruit[]
}

export function getMemberProfile(handle: string): Promise<MemberProfile> {
  return apiPost<MemberProfile>('/auth/memberProfile', { handle })
}
