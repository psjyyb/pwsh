import { apiPost } from '../../api/http'
import type { Bbs } from '../../adm/bbs/bbs.api'
import type { Recruit } from '../recruit/recruit.api'

/** 내가 쓴 글(전 게시판) */
export const myPosts = () => apiPost<Bbs[]>('/adm/bbs/selectBbsListMine.do', {})
/** 내가 연 모집 */
export const myRecruits = () => apiPost<Recruit[]>('/adm/recruit/selectRecruitListMine.do', {})
/** 본인 닉네임 변경 */
export const changeNickname = (nickname: string) => apiPost<void>('/auth/nickname', { nickname })
