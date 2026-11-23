import { apiPost } from '../../api/http'

/** 게시 승인 화면의 게시글 한 줄(CMS 1.2.41) — 관리자 화면이라 작성자 로그인 ID(regId)도 온다 */
export interface PostApproval {
  rowId?: string // PK(post_id)
  boardId?: string
  boardName?: string
  title?: string
  content?: string // 상세에서만
  regId?: string
  regName?: string
  replyYn?: string // 'Y'=답글
  approvalCd?: string // APPROVAL01 대기 / 02 승인 / 03 반려
  approvalName?: string
  approvalMemo?: string // 반려 사유
  approvalId?: string // 처리한 관리자
  approvalDt?: string
  resubmitYn?: string // 'Y'=처리된 뒤 작성자가 고쳐 다시 올린 글
  regDt?: string
  updDt?: string
}

const BASE = '/adm/postapproval'
export const POST_APPROVAL_LIST_URL = `${BASE}/selectPostApprovalList.do`

export const postApprovalApi = {
  view: (rowId: string) => apiPost<PostApproval>(`${BASE}/selectPostApprovalView.do`, { rowId }),
  /** 승인(APPROVAL02) 또는 반려(APPROVAL03, 사유 필수) — 대기 글만 처리된다(이미 처리된 글은 400) */
  decide: (rowId: string, approvalCd: 'APPROVAL02' | 'APPROVAL03', approvalMemo?: string) =>
    apiPost<void>(`${BASE}/updatePostApproval.do`, { rowId, approvalCd, approvalMemo }),
}
