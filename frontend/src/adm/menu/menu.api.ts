import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 메뉴 VO (백엔드 MenuVO 대응) */
export interface Menu {
  dbKey?: string // PK(menu_id)
  pMenuId?: string // 부모 메뉴 참조(FK)
  area?: string // ADM=관리자, GEN=사용자
  menuNm: string
  menuDesc?: string | null
  ordr?: string
  connTy?: string // MENU01=URL, MENU02=게시판, MENU03=페이지, MENU04=그룹
  connId?: string
  linkUrl?: string | null
  targetYn?: string
  icon?: string | null // 메뉴 아이콘 키(MenuGlyph 레지스트리)
  useYn?: string
  children?: Menu[] // 계층 트리(프론트 구성)
}

export const menuApi = {
  ...createCrudApi<Menu>('/adm/menu', 'Menu'),
  /** 사이드바용 트리(area별, 권한 필터 적용) — selectList{path=Tree} */
  tree: (area = 'ADM') => apiPost<Menu[]>('/adm/menu/selectMenuListTree.do', { area }),
  /** 관리 화면용 트리(권한필터 없이 area 전체) — selectList{path=ManageTree} */
  manageTree: (area = 'ADM') => apiPost<Menu[]>('/adm/menu/selectMenuListManageTree.do', { area }),
  /** 순서 변경(같은 부모 내 인접 메뉴와 교환) */
  moveOrdr: (dbKey: string, direction: 'UP' | 'DOWN') =>
    apiPost<void>('/adm/menu/updateMenuOrdr.do', { dbKey, searchCondition: direction }),
}

export const MENU_LIST_URL = menuApi.listUrl
