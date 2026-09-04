import { useCallback, useEffect, useState } from 'react'

/** 최근 사용 메뉴 1건. path=화면 경로(고유키), label=메뉴명(표시용) */
export interface RecentMenu {
  path: string
  label: string
}

const KEY = 'adm.recentMenus'
const MAX = 5

function read(): RecentMenu[] {
  try {
    const arr = JSON.parse(localStorage.getItem(KEY) || '[]')
    return Array.isArray(arr) ? (arr as RecentMenu[]) : []
  } catch {
    return []
  }
}

/**
 * 최근 사용 메뉴(브라우저별 localStorage, 최근 5개).
 *
 * 서버에 저장하지 않는 이유: 개인 화면 편의값이라 계정별 이력으로 남길 필요가 없고,
 * 저장하면 조회 API·테이블이 늘어난다. 브라우저를 바꾸면 초기화되는 게 정상 동작이다.
 *
 * record는 "메뉴명이 확정된 뒤"에 부르는 게 중요하다 — 메뉴 목록이 아직 안 온 시점에는
 * 경로가 이름으로 남아 목록에 `/adm/code` 같은 값이 찍힌다.
 */
export function useRecentMenus() {
  const [list, setList] = useState<RecentMenu[]>(read)

  // 다른 탭에서 바뀐 경우도 반영(같은 계정으로 두 탭을 열어두는 경우)
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === KEY) setList(read())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const record = useCallback((item: RecentMenu) => {
    if (!item.path || !item.label) return
    setList((prev) => {
      const next = [item, ...prev.filter((m) => m.path !== item.path)].slice(0, MAX)
      // 같은 메뉴를 다시 열었을 뿐이면 저장하지 않는다(불필요한 리렌더 방지)
      if (prev.length === next.length && prev.every((m, i) => m.path === next[i].path)) {
        return prev
      }
      try {
        localStorage.setItem(KEY, JSON.stringify(next))
      } catch {
        /* 저장 실패는 무시 */
      }
      return next
    })
  }, [])

  const clear = useCallback(() => {
    setList([])
    try {
      localStorage.removeItem(KEY)
    } catch {
      /* 무시 */
    }
  }, [])

  return { list, record, clear }
}
