import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 취미 카탈로그 VO */
export interface Hobby {
  rowId?: string // PK(hobby_id)
  hobbyNm?: string
  summary?: string
  intro?: string
  guide?: string
  difficultyCd?: string
  difficultyNm?: string
  equipment?: string
  estCost?: string
  bbsinfoId?: string
  bbsinfoNm?: string
  sortOrdr?: string
  thumbId?: string
  postCnt?: string
  memberCnt?: string
  useYn?: string
}

export const hobbyApi = {
  ...createCrudApi<Hobby>('/adm/hobby', 'Hobby'),
  /** 등록 후 생성된 취미 ID 반환(썸네일 매핑 연결용) */
  insertReturnId: (vo: Partial<Hobby>) => apiPost<string>('/adm/hobby/insertHobby.do', vo),
  /** 전체 취미(도감/드롭다운용) */
  listAll: () =>
    apiPost<ListResult<Hobby>>('/adm/hobby/selectHobbyList.do', { pageIndex: 1, size: 100 }).then((r) => r.list),
}
export const HOBBY_LIST_URL = hobbyApi.listUrl

/** 회원별 취미 레벨 */
export interface UserHobby {
  rowId?: string // = hobby_id
  hobbyId?: string
  levelCd?: string
  hobbyNm?: string
  levelNm?: string
}

export const userHobbyApi = {
  /** 내가 담은 취미 목록(관심=레벨 없음 포함) */
  list: () => apiPost<UserHobby[]>('/adm/userHobby/selectUserHobbyList.do', {}),
  /** 담기(관심) + 레벨(선택) upsert. levelCd 생략/undefined=관심만(레벨 없음), 값=레벨 지정. */
  save: (hobbyId: string, levelCd?: string) =>
    apiPost<void>('/adm/userHobby/insertUserHobby.do', { hobbyId, levelCd: levelCd ?? null }),
  /** 담기 취소(목록에서 제거) */
  remove: (hobbyId: string) => apiPost<void>('/adm/userHobby/deleteUserHobby.do', { hobbyId }),
}
