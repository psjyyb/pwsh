import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 권한그룹 VO */
export interface Authgrp {
  rowId?: string // 자기 PK(authgrp_id) — 자체 CRUD용(rowKey/조회/수정/삭제)
  authgrpId?: string // 매핑(t_auth·t_auth_user)·콤보 참조용
  authgrpNm: string
  authgrpDesc?: string | null
  useYn?: string
}

export const authgrpApi = {
  ...createCrudApi<Authgrp>('/adm/authgrp', 'Authgrp'),
  /** 콤보 옵션(사용자-그룹 지정용) — selectList{variant=Combo} */
  combo: () => apiPost<Authgrp[]>('/adm/authgrp/selectAuthgrpListCombo.do', {}),
  /** 그룹의 권한 메뉴 ID 목록 — selectList{variant=Menu} */
  getMenuIds: (authgrpId: string) => apiPost<string[]>('/adm/authgrp/selectAuthgrpListMenu.do', { authgrpId }),
  /** 그룹-메뉴 권한 저장 — update{variant=Menu} */
  saveMenu: (authgrpId: string, menuIds: string[]) =>
    apiPost<void>('/adm/authgrp/updateAuthgrpMenu.do', { authgrpId, menuIds }),
  /** 그룹의 소속 사용자 ID 목록 — selectList{variant=User} */
  getUserIds: (authgrpId: string) => apiPost<string[]>('/adm/authgrp/selectAuthgrpListUser.do', { authgrpId }),
  /** 그룹-사용자 지정 저장 — update{variant=User} */
  saveUsers: (authgrpId: string, userIds: string[]) =>
    apiPost<void>('/adm/authgrp/updateAuthgrpUser.do', { authgrpId, userIds }),
}

export const AUTHGRP_LIST_URL = authgrpApi.listUrl
