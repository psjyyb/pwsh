import { apiPost } from '../../api/http'

/** 댓글 VO. 작성자는 regId. */
export interface Comment {
  dbKey?: string // PK(comment_id)
  bbsId?: string
  pCommentId?: string // 부모 댓글(0/빈값=최상위, 값=대댓글)
  context?: string
  regId?: string
  regNm?: string // 작성자 표시명(닉네임). 없으면 regId로 폴백
  regProfileFileId?: string // 작성자 프로필 사진 file_id
  regDt?: string
  goodCnt?: string // 좋아요 수
  likedYn?: string // 내가 좋아요 눌렀는지(Y/N)
}

export const commentApi = {
  list: (bbsId: string) => apiPost<Comment[]>('/adm/comment/selectCommentList.do', { bbsId }),
  insert: (bbsId: string, context: string, pCommentId?: string) =>
    apiPost<void>('/adm/comment/insertComment.do', { bbsId, context, pCommentId }),
  update: (dbKey: string, context: string) => apiPost<void>('/adm/comment/updateComment.do', { dbKey, context }),
  remove: (dbKey: string) => apiPost<void>('/adm/comment/deleteComment.do', { dbKey }),
}
