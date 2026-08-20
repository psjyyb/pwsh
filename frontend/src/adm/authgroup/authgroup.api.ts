import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 권한그룹 VO */
export interface AuthGroup {
  rowId?: string // 자기 PK(auth_group_id) — 자체 CRUD용(rowKey/조회/수정/삭제)
  authGroupId?: string // 매핑(auth·auth_member)·콤보 참조용
  authGroupName: string
  description?: string | null
  useYn?: string
}

export const authGroupApi = {
  ...createCrudApi<AuthGroup>('/adm/authgroup', 'AuthGroup'),
  /** 콤보 옵션(사용자-그룹 지정용) — selectList{variant=Combo} */
  combo: () => apiPost<AuthGroup[]>('/adm/authgroup/selectAuthGroupListCombo.do', {}),
  /** 그룹의 권한 메뉴 ID 목록 — selectList{variant=Menu} */
  getMenuIds: (authGroupId: string) => apiPost<string[]>('/adm/authgroup/selectAuthGroupListMenu.do', { authGroupId }),
  /** 그룹-메뉴 권한 저장 — update{variant=Menu} */
  saveMenu: (authGroupId: string, menuIds: string[]) =>
    apiPost<void>('/adm/authgroup/updateAuthGroupMenu.do', { authGroupId, menuIds }),
  /** 그룹의 소속 사용자 ID 목록 — selectList{variant=User} */
  getMemberIds: (authGroupId: string) => apiPost<string[]>('/adm/authgroup/selectAuthGroupListMember.do', { authGroupId }),
  /** 그룹-사용자 지정 저장 — update{variant=User} */
  saveMembers: (authGroupId: string, memberIds: string[]) =>
    apiPost<void>('/adm/authgroup/updateAuthGroupMember.do', { authGroupId, memberIds }),
}

export const AUTH_GROUP_LIST_URL = authGroupApi.listUrl
