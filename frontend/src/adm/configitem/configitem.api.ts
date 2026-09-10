import { apiPost } from '../../api/http'

/** 확장 설정 항목. 정의(name·inputType·groupCd)는 서버가 주고 화면은 그걸 보고 그린다. */
export interface ConfigItem {
  configKey: string
  value?: string
  inputType?: 'TEXT' | 'TEXTAREA' | 'NUMBER' | 'YN'
  name?: string
  description?: string
  groupCd?: string
  sortNo?: string
  publicYn?: string
}

const BASE = '/adm/configitem'

export const CONFIGITEM_LIST_URL = `${BASE}/selectConfigItemList.do`

/**
 * 항목 정의는 개발자가 data.sql로 넣고 화면에서는 값만 바꾼다 → 등록/삭제 API가 없다.
 * (운영자가 키를 임의로 만들면 그 키를 읽는 코드가 없어 효과 없는 설정만 쌓인다)
 */
export const configItemApi = {
  list: () => apiPost<ConfigItem[]>(CONFIGITEM_LIST_URL, {}),
  updateValues: (items: Pick<ConfigItem, 'configKey' | 'value'>[]) =>
    apiPost<void>(`${BASE}/updateConfigItem.do`, items),
}

/** 공개 항목만(비로그인 포함) — 사용자 화면이 푸터 문구·메인 설정을 읽는 용도. */
export const pubConfigItemApi = {
  list: () => apiPost<ConfigItem[]>('/pub/configitem', {}),
}
