/**
 * 게시글 조회수 중복방지 — 브라우저 localStorage에 조회한 글 id + 만료시각 기록.
 * 브라우저 기준 1일 1회만 조회수를 올린다(서버 세션 대신 클라이언트 판정 — SPA라 새로고침이 잦다).
 */
const KEY = 'bbsViewed'
const TTL_MS = 24 * 60 * 60 * 1000 // 1일

type ViewedMap = Record<string, number> // bbsId -> 만료 epoch(ms)

function load(): ViewedMap {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '{}') as ViewedMap
  } catch {
    return {}
  }
}

function save(m: ViewedMap) {
  try {
    localStorage.setItem(KEY, JSON.stringify(m))
  } catch {
    /* 저장 실패(용량/프라이빗 모드 등)는 무시 — 조회수 중복방지는 부가기능 */
  }
}

/** 만료 항목 제거 후 맵 반환 */
function prune(m: ViewedMap): ViewedMap {
  const now = Date.now()
  let changed = false
  for (const k of Object.keys(m)) {
    if (m[k] <= now) {
      delete m[k]
      changed = true
    }
  }
  if (changed) save(m)
  return m
}

/** 최근(1일 내) 조회한 글이면 true → 조회수 증가하지 않음 */
export function hasViewedRecently(bbsId: string): boolean {
  const m = prune(load())
  return !!m[bbsId] && m[bbsId] > Date.now()
}

/** 조회 기록(만료시각 갱신) */
export function markViewed(bbsId: string): void {
  const m = prune(load())
  m[bbsId] = Date.now() + TTL_MS
  save(m)
}
