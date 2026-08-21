import { apiPost } from '../../api/http'

/** 댓글 VO. 작성자는 regId. */
export interface Comment {
  rowId?: string // PK(comment_id)
  postId?: string
  pCommentId?: string // 부모 댓글(0/빈값=최상위, 값=대댓글)
  content?: string
  regId?: string // 작성자 로그인 ID — 관리자 화면 전용(사용자(GEN) 응답에는 내려오지 않음)
  regName?: string // 작성자 표시명(닉네임)
  regHandle?: string // 작성자 공개 식별자 — 프로필 링크용(GEN)
  mineYn?: string // 'Y'=내가 쓴 댓글(서버 계산)
  regProfileFileId?: string // 작성자 프로필 사진 file_id
  regDt?: string
  goodCnt?: string // 좋아요 수
  likedYn?: string // 내가 좋아요 눌렀는지(Y/N)
}

export const commentApi = {
  list: (postId: string) => apiPost<Comment[]>('/adm/comment/selectCommentList.do', { postId }),
  insert: (postId: string, content: string, pCommentId?: string) =>
    apiPost<void>('/adm/comment/insertComment.do', { postId, content, pCommentId }),
  update: (rowId: string, content: string) => apiPost<void>('/adm/comment/updateComment.do', { rowId, content }),
  remove: (rowId: string) => apiPost<void>('/adm/comment/deleteComment.do', { rowId }),
}
