import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 배너 VO — title의 `[[...]]`는 강조 구간, 줄바꿈은 그대로 표시(마커일 뿐 HTML이 아니다) */
export interface Banner {
  rowId?: string // PK(banner_id)
  title?: string
  description?: string
  btn1Label?: string
  btn1Url?: string
  btn2Label?: string
  btn2Url?: string
  startDt?: string
  endDt?: string
  sortNo?: string
  fileId?: string // 배경 이미지(file)
  useYn?: string
}

export const bannerApi = {
  ...createCrudApi<Banner>('/adm/banner', 'Banner'),
  /** 순서 변경(인접 배너와 교환) */
  moveSort: (rowId: string, direction: 'UP' | 'DOWN') =>
    apiPost<void>('/adm/banner/updateBannerSort.do', { rowId, direction }),
  /** 사용자 메인 노출용(사용중+기간내) — 비로그인도 호출한다 */
  mainList: () => apiPost<Banner[]>('/adm/banner/selectBannerListMain.do', {}),
}
export const BANNER_LIST_URL = bannerApi.listUrl
