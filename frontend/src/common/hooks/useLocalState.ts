import { useCallback, useState } from 'react'

/**
 * localStorage에 값을 유지하는 useState. 사이드바 접힘·글자크기처럼
 * "이 브라우저에서만 기억하면 되는" 화면 설정에 쓴다(서버 저장 아님).
 *
 * 저장 실패(용량 초과·시크릿 모드 등)는 무시한다 — 설정이 안 남는 것뿐이고
 * 화면은 그대로 동작해야 한다.
 */
export function useLocalState<T>(key: string, initial: T) {
  const [value, setValue] = useState<T>(() => {
    try {
      const raw = localStorage.getItem(key)
      return raw === null ? initial : (JSON.parse(raw) as T)
    } catch {
      return initial
    }
  })

  const set = useCallback(
    (next: T) => {
      setValue(next)
      try {
        localStorage.setItem(key, JSON.stringify(next))
      } catch {
        /* 저장 못 해도 화면 동작에는 지장 없음 */
      }
    },
    [key],
  )

  return [value, set] as const
}
