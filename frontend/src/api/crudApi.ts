import { apiPost } from './http'

export interface CrudApi<T> {
  listUrl: string
  view: (rowId: string) => Promise<T>
  insert: (vo: Partial<T>) => Promise<void>
  update: (vo: Partial<T>) => Promise<void>
  remove: (rowId: string) => Promise<void>
}

/**
 * 표준 CRUD 도메인 API 생성기. base(예: /adm/code) + name(예: Code)으로
 * select{Name}List / select{Name}View / insert{Name} / update{Name} / delete{Name} .do 를 조립.
 *   export const codeApi = createCrudApi<Code>('/adm/code', 'Code')
 *   export const CODE_LIST_URL = codeApi.listUrl
 * 도메인 고유 API는 스프레드로 확장: { ...createCrudApi(...), tree: ... }
 */
export function createCrudApi<T>(base: string, name: string): CrudApi<T> {
  return {
    listUrl: `${base}/select${name}List.do`,
    view: (rowId) => apiPost<T>(`${base}/select${name}View.do`, { rowId }),
    insert: (vo) => apiPost<void>(`${base}/insert${name}.do`, vo),
    update: (vo) => apiPost<void>(`${base}/update${name}.do`, vo),
    remove: (rowId) => apiPost<void>(`${base}/delete${name}.do`, { rowId }),
  }
}
