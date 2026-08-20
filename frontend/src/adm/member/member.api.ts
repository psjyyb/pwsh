import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 사용자 VO (백엔드 MemberVO 대응, 비밀번호는 등록/변경 시에만) */
export interface Member {
  rowId?: string // 자기 PK(member_id) — 자체 CRUD용(rowKey/조회/삭제)
  memberId?: string // 로그인 ID(등록·표시·매핑 참조용)
  password?: string
  typeCd?: string
  memberName: string
  phone?: string | null
  email?: string | null
  genderCd?: string | null
  birth?: string | null
  statusCd?: string
  useYn?: string
  memberTypeName?: string // 회원유형명(목록 표시, 조회 전용)
  statusCdName?: string // 계정상태명(목록 표시, 조회 전용)
  followerCnt?: string // 팔로워 수(상세 조회 계산값)
  followingCnt?: string // 팔로잉 수(상세 조회 계산값)
  // 폼 전용(등록/수정 UI) — 서버로는 password만 사용
  changePw?: boolean
  memberPwConfirm?: string
}

export const memberApi = {
  ...createCrudApi<Member>('/adm/member', 'Member'),
  /** 비밀번호 변경 (rowId=member_id) */
  changePassword: (rowId: string, password: string) =>
    apiPost<void>('/adm/member/updateMemberPassword.do', { rowId, password }),
  /** 사용자의 권한그룹 ID 목록 — selectList{variant=AuthGroup} */
  getAuthGroups: (memberId: string) => apiPost<string[]>('/adm/member/selectMemberListAuthGroup.do', { memberId }),
  /** 사용자-권한그룹 저장 — update{variant=AuthGroup} */
  saveAuthGroups: (memberId: string, authGroupIds: string[]) =>
    apiPost<void>('/adm/member/updateMemberAuthGroup.do', { memberId, authGroupIds }),
  /** 강제 로그아웃 — token_ver를 올려 해당 사용자의 발급 토큰 즉시 무효화 (update{variant=ForceLogout}) */
  forceLogout: (memberId: string) =>
    apiPost<void>('/adm/member/updateMemberForceLogout.do', { memberId }),
  /** 제재: 정지(STATUS03)/해제(STATUS01) — 정지 시 세션 즉시 무효화 (update{variant=Status}) */
  changeStatus: (memberId: string, statusCd: 'STATUS01' | 'STATUS03') =>
    apiPost<void>('/adm/member/updateMemberStatus.do', { memberId, statusCd }),
}

export const MEMBER_LIST_URL = memberApi.listUrl
