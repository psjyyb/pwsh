import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 팝업 VO */
export interface Popup {
  rowId?: string // PK(popup_id)
  popupName?: string
  startDt?: string
  endDt?: string
  linkUrl?: string
  content?: string
  sortNo?: string
  width?: string
  height?: string
  posTop?: string
  posLeft?: string
  fileId?: string // 팝업 이미지(file)
  useYn?: string
}

export const popupApi = {
  ...createCrudApi<Popup>('/adm/popup', 'Popup'),
  /** 순서 변경(인접 팝업과 교환) */
  moveSort: (rowId: string, direction: 'UP' | 'DOWN') =>
    apiPost<void>('/adm/popup/updatePopupSort.do', { rowId, direction }),
  /** 사용자 메인 노출용(사용중+기간내) 팝업 목록 — selectList{variant=Main} */
  mainList: () => apiPost<Popup[]>('/adm/popup/selectPopupListMain.do', {}),
}
export const POPUP_LIST_URL = popupApi.listUrl
