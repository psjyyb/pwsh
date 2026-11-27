import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 자주찾는 서비스(CMS 1.2.45) — 사용자 메인 히어로 아래 바로가기 버튼 */
export interface QuickLink {
  rowId?: string // PK(quick_link_id)
  name?: string
  linkUrl?: string
  icon?: string // MenuGlyph 아이콘 키(메뉴 아이콘과 같은 목록)
  targetYn?: string // Y=새 창
  displayYn?: string // N=메인에서만 숨김(순서 자리는 지킨다)
  sortNo?: string
}

/**
 * 주소 규칙 — 서버(QuickLinkService)와 같다. '/'로 시작하는 사이트 안 경로(다음 글자가 '/'·'\'면 안 된다 — 브라우저가 다른 사이트로 읽는다)
 * 또는 http(s)://호스트. javascript: 같은 값이 링크가 되면 누르는 방문자 화면에서 스크립트가 돈다.
 * 관리 화면 입력 검사와 메인 화면 렌더(DB를 직접 고친 값 거르기)가 함께 쓴다 — 서버 정규식과 따로 바꾸지 않는다.
 */
const INTERNAL = /^\/(?![/\\])[^\s\p{Cc}]*$/u
const EXTERNAL = /^https?:\/\/[^\s\p{Cc}/?#\\]+([/?#][^\s\p{Cc}]*)?$/u
export const isSafeQuickLinkUrl = (url?: string) => !!url && url.length <= 255 && (INTERNAL.test(url) || EXTERNAL.test(url))
export const isInternalQuickLink = (url: string) => INTERNAL.test(url)

export const quickLinkApi = {
  ...createCrudApi<QuickLink>('/adm/quicklink', 'QuickLink'),
  /** 순서 변경(인접 행과 교환 — 숨긴 것도 자리를 가진다) */
  moveSort: (rowId: string, direction: 'UP' | 'DOWN') =>
    apiPost<void>('/adm/quicklink/updateQuickLinkSort.do', { rowId, direction }),
  /** 사용자 메인 노출용(삭제 안 됨 + 노출) — 비로그인도 호출한다 */
  mainList: () => apiPost<QuickLink[]>('/adm/quicklink/selectQuickLinkListMain.do', {}),
}
export const QUICK_LINK_LIST_URL = quickLinkApi.listUrl
