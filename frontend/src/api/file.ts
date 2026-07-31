import client from './client'
import { apiPost } from './http'

export interface FileMeta {
  fileId?: string
  fileOrgNm?: string
  fileExt?: string
  fileSize?: string
  fileDesc?: string
  regId?: string
  regDt?: string
  useYn?: string
}

/** 목록 URL (파일 관리 화면 useList용) */
export const FILE_LIST_URL = '/adm/file/selectFileList.do'

/** 파일 전송 계층 (업로드/다운로드는 multipart/blob이라 client 직접 사용) */
export const fileApi = {
  /** 다중 업로드 → 생성된 파일 메타 반환 */
  upload: async (files: File[]): Promise<FileMeta[]> => {
    const fd = new FormData()
    files.forEach((f) => fd.append('files', f))
    const res = await client.post<{ data: FileMeta[] }>('/adm/file/upload.do', fd)
    return res.data.data
  },
  /** 다운로드 (인증 헤더 필요 → blob으로 받아 저장) */
  download: async (fileId: string, fileName: string) => {
    const res = await client.get('/adm/file/download.do', { params: { fileId }, responseType: 'blob' })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  },
  remove: (fileId: string) => apiPost<void>('/adm/file/deleteFile.do', { fileId }),
  /** 에디터 본문 이미지 업로드 → 공개 서빙 URL(/api/pub/image/{id}) 반환 */
  uploadImage: async (blob: Blob, fileName = 'image'): Promise<string> => {
    const fd = new FormData()
    fd.append('file', blob, blob instanceof File ? blob.name : fileName)
    const res = await client.post<{ data: { url: string } }>('/adm/file/imageUpload.do', fd)
    return res.data.data.url
  },
  /** 이미지 미리보기용 object URL (인증 헤더로 blob 수신 → URL 생성). 사용 후 revokeObjectURL 권장 */
  imageUrl: async (fileId: string): Promise<string> => {
    const res = await client.get('/adm/file/download.do', { params: { fileId }, responseType: 'blob' })
    return URL.createObjectURL(res.data as Blob)
  },
  /** 엔티티(map_key+file_loc) 연결 파일 목록 */
  listByMap: (mapKey: string, fileLoc: string) =>
    apiPost<FileMeta[]>('/adm/file/selectFileMapList.do', { mapKey, fileLoc }),
  /** 엔티티-파일 매핑 저장. fileDescs 전달 시 파일별 설명(캡션)도 갱신(갤러리) */
  saveMapping: (mapKey: string, fileLoc: string, fileIds: string[], fileDescs?: string[]) =>
    apiPost<void>('/adm/file/saveFileMapping.do', { mapKey, fileLoc, fileIds, fileDescs }),
}
