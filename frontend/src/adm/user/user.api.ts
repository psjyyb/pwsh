import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 사용자 VO (백엔드 UserVO 대응, 비밀번호는 등록/변경 시에만) */
export interface User {
  dbKey?: string // 자기 PK(user_id) — 자체 CRUD용(rowKey/조회/삭제)
  userId?: string // 로그인 ID(등록·표시·매핑 참조용)
  userPw?: string
  memCd?: string
  userNm: string
  phone?: string | null
  email?: string | null
  genCd?: string | null
  birth?: string | null
  statusCd?: string
  useYn?: string
  memCdNm?: string // 회원유형명(목록 표시, 조회 전용)
  statusCdNm?: string // 계정상태명(목록 표시, 조회 전용)
  // 폼 전용(등록/수정 UI) — 서버로는 userPw만 사용
  changePw?: boolean
  userPwConfirm?: string
}

export const userApi = {
  ...createCrudApi<User>('/adm/user', 'User'),
  /** 비밀번호 변경 (dbKey=user_id) */
  changePassword: (dbKey: string, userPw: string) =>
    apiPost<void>('/adm/user/updateUserPassword.do', { dbKey, userPw }),
  /** 사용자의 권한그룹 ID 목록 — selectList{path=Authgrp} */
  getAuthgrps: (userId: string) => apiPost<string[]>('/adm/user/selectUserListAuthgrp.do', { userId }),
  /** 사용자-권한그룹 저장 — update{path=Authgrp} */
  saveAuthgrps: (userId: string, authgrpIds: string[]) =>
    apiPost<void>('/adm/user/updateUserAuthgrp.do', { userId, authgrpIds }),
  /** 강제 로그아웃 — token_ver를 올려 해당 사용자의 발급 토큰 즉시 무효화 (update{path=ForceLogout}) */
  forceLogout: (userId: string) =>
    apiPost<void>('/adm/user/updateUserForceLogout.do', { userId }),
}

export const USER_LIST_URL = userApi.listUrl
