import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 게시판 정의(설정) VO */
export interface Bbsinfo {
  rowId?: string // PK(bbsinfo_id)
  bbsinfoNm?: string
  bbsinfoCd?: string
  bbsinfoCdNm?: string // 유형명(목록 표시)
  bbsinfoDesc?: string
  listCnt?: string
  fileYn?: string
  fileCnt?: string
  fileSize?: string
  noticeYn?: string
  newCnt?: string
  useYn?: string
}

export const bbsinfoApi = {
  ...createCrudApi<Bbsinfo>('/adm/bbsinfo', 'Bbsinfo'),
  /** 게시판 콤보(메뉴 게시판 연결 선택용) — selectList{variant=Combo} */
  comboList: () => apiPost<Bbsinfo[]>('/adm/bbsinfo/selectBbsinfoListCombo.do', {}),
}
export const BBSINFO_LIST_URL = bbsinfoApi.listUrl
