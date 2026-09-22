import client from '../../api/client'
import { apiPost } from '../../api/http'

/** 목록 URL — 전송 계층(업로드·다운로드·미리보기)은 api/file.ts를 그대로 쓴다 */
export { FILE_LIST_URL } from '../../api/file'

/** 이미지로 미리보기를 띄울 확장자. 서버(PubImageController·FileService)와 같은 목록을 본다 */
const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']

export function isImage(ext?: string) {
  return !!ext && IMAGE_EXTS.includes(ext.toLowerCase())
}

/** 파일 VO (백엔드 FileVO 대응) */
export interface MediaFile {
  fileId?: string
  originalName?: string
  ext?: string
  size?: string
  description?: string
  regId?: string
  regDt?: string
  useYn?: string
  refCnt?: string // 이 파일을 쓰는 곳 수(LIBRARY 매핑 제외, 조회 전용)
  libraryYn?: string // 'Y'=라이브러리에 담긴 파일(조회 전용)
}

/** 사용처 1건 */
export interface MediaRef {
  fileType?: string
  mapKey?: string // PROFILE은 file_ref가 아니라 member 직접 참조라 값이 없다
  targetName?: string // 게시글 제목 / 회원 닉네임. 없으면 용도+id로 표시
}

/**
 * 용도 코드 → 화면 문구. 서버의 `file_ref.file_type`과 짝을 맞춘다.
 * `PROFILE`만 예외로 file_ref에 없다 — 프로필 사진은 `member.profile_file_id` 직접 참조라
 * 서버가 사용처 조회에서 UNION으로 끼워 넣는다.
 */
export const FILE_TYPE_LABEL: Record<string, string> = {
  POST: '게시글 첨부',
  POST_IMG: '게시글 대표이미지',
  POST_EDITOR: '게시글 본문이미지',
  POPUP: '팝업',
  LOGO: '로고',
  BANNER: '배너',
  HOBBY: '취미 대표이미지',
  HOBBY_EDITOR: '취미 본문이미지',
  PROFILE: '회원 프로필 사진',
}

export const mediaApi = {
  /** 파일 1건의 사용처 목록 — selectList{variant=Ref} */
  refs: (fileId: string) => apiPost<MediaRef[]>('/adm/file/selectFileListRef.do', { fileId }),
  /** 라이브러리 업로드(다중) — 저장과 동시에 LIBRARY 매핑까지 걸린다 */
  uploadLibrary: async (files: File[]): Promise<MediaFile[]> => {
    const fd = new FormData()
    files.forEach((f) => fd.append('files', f))
    const res = await client.post<{ data: MediaFile[] }>('/adm/file/uploadLibrary.do', fd)
    return res.data.data
  },
  /** 라이브러리 담기/빼기 — update{variant=Library} */
  setLibrary: (fileId: string, add: boolean) =>
    apiPost<void>('/adm/file/updateFileLibrary.do', { fileId, useYn: add ? 'Y' : 'N' }),
  /** 삭제(논리) */
  remove: (fileId: string) => apiPost<void>('/adm/file/deleteFile.do', { fileId }),
  /** 고아 파일 즉시 정리 → 물리삭제 건수 */
  gc: () => apiPost<number>('/adm/file/gc.do', {}),
}
