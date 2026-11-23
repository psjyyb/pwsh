import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 게시판 정의(설정) VO */
export interface Board {
  rowId?: string // PK(board_id)
  boardName?: string
  typeCd?: string
  boardCdName?: string // 유형명(목록 표시)
  description?: string
  listCnt?: string
  fileYn?: string
  fileCntLimit?: string
  fileSizeLimitMb?: string
  noticeYn?: string
  newCnt?: string
  /** 게시 승인 사용(CMS 1.2.41) — Y면 회원 글은 관리자 승인 전까지 작성자·관리자만 본다(취미 게시판도 같다) */
  approvalYn?: string
  useYn?: string
}

export const boardApi = {
  ...createCrudApi<Board>('/adm/board', 'Board'),
  /** 게시판 콤보(메뉴 게시판 연결·취미 게시판 지정용, 관리자 전용) — selectList{variant=Combo} */
  comboList: () => apiPost<Board[]>('/adm/board/selectBoardListCombo.do', {}),
}
export const BOARD_LIST_URL = boardApi.listUrl
