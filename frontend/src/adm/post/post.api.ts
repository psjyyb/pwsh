import { apiPost } from '../../api/http'

/** 게시글 VO. 작성자는 regId. */
export interface Post {
  rowId?: string // PK(post_id)
  boardId?: string
  title?: string
  content?: string
  searchSnippet?: string // 통합검색에서 본문이 매칭된 경우의 발췌(태그 제거된 평문)
  fileId?: string // 대표 이미지(갤러리 썸네일) post.file_id
  noticeYn?: string
  noticeStartDt?: string
  noticeEndDt?: string
  secretYn?: string
  password?: string // 비밀글 비밀번호(작성/열람 시 전송, 서버는 조회 응답에 미포함)
  secretLocked?: string // 'Y'=비밀글 잠금(비번 필요) — 서버 응답 전용
  viewCnt?: string
  goodCnt?: string
  likedYn?: string // 내가 좋아요 눌렀는지(Y/N) — 상세 조회 응답
  regId?: string // 작성자 로그인 ID — 관리자 화면 전용(사용자(GEN) 응답에는 내려오지 않음)
  regName?: string // 작성자 표시명(닉네임)
  regHandle?: string // 작성자 공개 식별자 — 프로필 링크용(GEN)
  mineYn?: string // 'Y'=내가 쓴 글(서버 계산) — 수정/삭제·신고노출 판정(GEN)
  regProfileFileId?: string // 작성자 프로필 사진 file_id
  regDt?: string
  commentCnt?: string
  boardName?: string // 게시판명(마이페이지·검색 결과 표시)
  noticeEff?: string // 'Y'=공지 유효(목록 상단)
  pPostId?: string // 답글: 원글 post_id(작성 시 전송)
  depth?: string // 스레드 들여쓰기 레벨(0=원글) — 목록 조회 응답
}

export const POST_LIST_URL = '/adm/post/selectPostList.do'

export const postApi = {
  view: (rowId: string, password?: string, viewUp = false) =>
    apiPost<Post>('/adm/post/selectPostView.do', { rowId, password, viewUp: viewUp ? 'Y' : 'N' }),
  /** 등록 → 생성된 게시글 ID 반환 */
  insert: (vo: Partial<Post>) => apiPost<string>('/adm/post/insertPost.do', vo),
  update: (vo: Partial<Post>) => apiPost<void>('/adm/post/updatePost.do', vo),
  remove: (rowId: string) => apiPost<void>('/adm/post/deletePost.do', { rowId }),
}
