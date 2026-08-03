import { apiPost } from '../../api/http'

/** 게시글 VO. 작성자는 regId. */
export interface Bbs {
  dbKey?: string // PK(bbs_id)
  bbsinfoId?: string
  title?: string
  context?: string
  fileId?: string // 대표 이미지(갤러리 썸네일) t_bbs.file_id
  noticeYn?: string
  noticeStartDt?: string
  noticeEndDt?: string
  secretYn?: string
  bbsPw?: string // 비밀글 비밀번호(작성/열람 시 전송, 서버는 조회 응답에 미포함)
  secretLocked?: string // 'Y'=비밀글 잠금(비번 필요) — 서버 응답 전용
  viewCnt?: string
  goodCnt?: string
  likedYn?: string // 내가 좋아요 눌렀는지(Y/N) — 상세 조회 응답
  regId?: string // 작성자(로그인 ID)
  regNm?: string // 작성자 표시명(닉네임). 없으면 regId로 폴백
  regProfileFileId?: string // 작성자 프로필 사진 file_id
  regDt?: string
  bbsDt?: string
  commentCnt?: string
  bbsinfoNm?: string // 게시판명(마이페이지·검색 결과 표시)
  noticeEff?: string // 'Y'=공지 유효(목록 상단)
  pBbsId?: string // 답글: 원글 bbs_id(작성 시 전송)
  bbsDepth?: string // 스레드 들여쓰기 레벨(0=원글) — 목록 조회 응답
}

export const BBS_LIST_URL = '/adm/bbs/selectBbsList.do'

export const bbsApi = {
  view: (dbKey: string, bbsPw?: string, viewUp = false) =>
    apiPost<Bbs>('/adm/bbs/selectBbsView.do', { dbKey, bbsPw, viewUp: viewUp ? 'Y' : 'N' }),
  /** 등록 → 생성된 게시글 ID 반환 */
  insert: (vo: Partial<Bbs>) => apiPost<string>('/adm/bbs/insertBbs.do', vo),
  update: (vo: Partial<Bbs>) => apiPost<void>('/adm/bbs/updateBbs.do', vo),
  remove: (dbKey: string) => apiPost<void>('/adm/bbs/deleteBbs.do', { dbKey }),
}
