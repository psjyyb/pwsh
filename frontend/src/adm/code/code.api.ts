import { apiPost } from '../../api/http'
import { createCrudApi } from '../../api/crudApi'

/** 공통코드 VO (백엔드 CodeVO 대응) */
export interface Code {
  dbKey?: string // 자기 PK(code_id) — 자체 CRUD용
  pCodeId: string // 부모 코드 참조(FK)
  codeNm: string
  codeDesc?: string | null
  ordr?: string
  useYn?: string
  children?: Code[] // 계층 트리(프론트 구성)
}

export const codeApi = {
  ...createCrudApi<Code>('/adm/code', 'Code'),
  /** 계층 트리용 전체 조회(페이징 없음, 프론트에서 중첩) — selectList{path=Tree} */
  tree: () => apiPost<Code[]>('/adm/code/selectCodeListTree.do', {}),
  /** 하위코드추가용 다음 코드ID/순서 계산 — selectView{path=NextChild} */
  nextChild: (pCodeId: string) => apiPost<Code>('/adm/code/selectCodeViewNextChild.do', { pCodeId }),
  /** 순서 변경(같은 부모 내 인접 코드와 교환) — 표준 CMS 위로/아래로 방식 */
  moveOrdr: (dbKey: string, direction: 'UP' | 'DOWN') =>
    apiPost<void>('/adm/code/updateCodeOrdr.do', { dbKey, searchCondition: direction }),
}
export const CODE_LIST_URL = codeApi.listUrl
