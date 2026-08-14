import { useEffect, useState } from 'react'
import { apiPost } from '../../api/http'

export interface CodeOption {
  value: string
  label: string
}

interface CodeRow {
  codeId: string
  codeNm: string
}

/**
 * 공통코드 그룹(pCodeId) 하위 코드를 Select 옵션으로 동적 로드 — 코드값 하드코딩을 없앤다.
 * 예: useCodes('MENU00') → 메뉴 연결유형, useCodes('MEM00') → 회원유형, useCodes('STATUS00') → 계정상태.
 */
export function useCodes(pCodeId: string): CodeOption[] {
  const [options, setOptions] = useState<CodeOption[]>([])

  useEffect(() => {
    if (!pCodeId) return
    apiPost<CodeRow[]>('/adm/code/selectCodeListCombo.do', { pCodeId })
      .then((list) => setOptions(list.map((c) => ({ value: c.codeId, label: c.codeNm }))))
      .catch(() => setOptions([]))
  }, [pCodeId])

  return options
}
