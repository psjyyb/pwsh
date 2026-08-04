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

/** 회원 공개 프로필(PII 미포함). */
export interface UserProfile {
  userId?: string
  nickname?: string
  profileFileId?: string
  hobbies?: ProfileHobby[]
  posts?: ProfilePost[]
  recruits?: ProfileRecruit[]
}

export function getUserProfile(userId: string): Promise<UserProfile> {
  return apiPost<UserProfile>('/auth/userProfile', { userId })
}
