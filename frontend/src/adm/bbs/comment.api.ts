import { apiPost } from '../../api/http'

/** 댓글 VO. 작성자는 regId. */
export interface Comment {
  dbKey?: string // PK(comment_id)
  bbsId?: string
  context?: string
  regId?: string
  regNm?: string // 작성자 표시명(닉네임). 없으면 regId로 폴백
  regDt?: string
}

export const commentApi = {
  list: (bbsId: string) => apiPost<Comment[]>('/adm/comment/selectCommentList.do', { bbsId }),
  insert: (bbsId: string, context: string) => apiPost<void>('/adm/comment/insertComment.do', { bbsId, context }),
  update: (dbKey: string, context: string) => apiPost<void>('/adm/comment/updateComment.do', { dbKey, context }),
  remove: (dbKey: string) => apiPost<void>('/adm/comment/deleteComment.do', { dbKey }),
}
