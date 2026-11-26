/**
 * 분석 도구(CMS 1.2.44) — GA4 · 네이버 애널리틱스. ID는 확장설정(analytics.*, 공개)에서 오고, 스크립트는 여기서 만든다.
 *
 * 사용자 결정:
 *  - 관리자가 아무 코드나 넣는 '커스텀 스크립트'는 두지 않는다 — 관리자 계정이 털리면 모든 방문자 화면에서 그 코드가 돌고
 *    localStorage의 로그인 토큰을 가져갈 수 있다. ID만 받아 정해진 주소의 스크립트만 붙인다.
 *  - ★ 방문자가 동의한 뒤에만 불러온다. 동의 전·거부면 스크립트 태그 자체를 만들지 않는다(쿠키가 생기지 않게).
 *  - 사용자 화면(GenLayout)에서만 부른다 — 관리자 화면은 추적하지 않는다.
 *  - 개발 서버(vite dev)에서는 불러오지 않는다 — 개발 중 화면 이동이 운영 통계에 섞이지 않게(빌드한 산출물에서만).
 */

declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: (...args: unknown[]) => void
    wcs_add?: Record<string, string>
    wcs?: unknown
    wcs_do?: () => void
  }
}

/** 서버 저장 검사(ConfigItemService.FORMATS)와 같은 형식 — DB를 직접 고쳐 틀린 값이 들어와도 붙이지 않는다 */
const GA4_ID = /^G-[A-Z0-9]{4,20}$/
const NAVER_ID = /^[A-Za-z0-9_-]{4,40}$/

const CONSENT_KEY = 'analytics-consent'

export type Consent = 'Y' | 'N' | null

export interface AnalyticsIds {
  ga4Id?: string
  naverId?: string
}

/** 확장설정 값 → 쓸 수 있는 ID만(형식이 틀리면 빼고 경고) */
export function analyticsIds(ga4?: string, naver?: string): AnalyticsIds {
  const ids: AnalyticsIds = {}
  const g = ga4?.trim()
  const n = naver?.trim()
  if (g) {
    if (GA4_ID.test(g)) ids.ga4Id = g
    else console.warn('[analytics] GA4 측정 ID 형식이 맞지 않아 쓰지 않습니다:', g)
  }
  if (n) {
    if (NAVER_ID.test(n)) ids.naverId = n
    else console.warn('[analytics] 네이버 애널리틱스 ID 형식이 맞지 않아 쓰지 않습니다:', n)
  }
  return ids
}

export const hasAnalytics = (ids: AnalyticsIds) => !!(ids.ga4Id || ids.naverId)

/** 방문자 선택 — 브라우저별(localStorage). 저장소를 못 쓰는 환경이면 '아직 안 고름'으로 본다(그래도 동의 없이 불러오지는 않는다) */
export function getConsent(): Consent {
  try {
    const v = localStorage.getItem(CONSENT_KEY)
    return v === 'Y' || v === 'N' ? v : null
  } catch {
    return null
  }
}

export function saveConsent(v: 'Y' | 'N') {
  try {
    localStorage.setItem(CONSENT_KEY, v)
  } catch {
    /* 저장 못 하면 다음 방문에 다시 묻는다 */
  }
}

let gaLoaded: string | null = null
let naverLoaded = false

function addScript(src: string, onload?: () => void) {
  const s = document.createElement('script')
  s.async = true
  s.src = src
  if (onload) s.onload = onload
  document.head.appendChild(s)
}

/** 동의한 방문자에게 스크립트를 붙인다(한 번만). 첫 화면 집계는 각 스크립트가 불러온 직후 스스로 한다 */
export function startAnalytics(ids: AnalyticsIds) {
  if (!import.meta.env.PROD || getConsent() !== 'Y') return
  if (ids.ga4Id && !gaLoaded) {
    gaLoaded = ids.ga4Id
    const dataLayer = (window.dataLayer = window.dataLayer || [])
    // gtag는 arguments 객체 그대로 넣어야 한다(배열로 바꾸면 GA가 명령으로 읽지 않는다)
    window.gtag = function gtag() {
      dataLayer.push(arguments)
    }
    window.gtag('js', new Date())
    // 화면 이동(pushState)은 GA4 향상된 측정의 '브라우저 기록 기반 페이지 변경'(기본 켜짐)이 잡는다.
    // 여기서 page_view를 따로 보내면 그 설정과 겹쳐 두 번 센다.
    window.gtag('config', ids.ga4Id)
    addScript(`https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(ids.ga4Id)}`)
  }
  if (ids.naverId && !naverLoaded) {
    naverLoaded = true
    window.wcs_add = window.wcs_add || {}
    window.wcs_add.wa = ids.naverId
    addScript('https://wcs.naver.net/wcslog.js', () => window.wcs_do?.())
  }
}

/** 화면 이동 집계 — 네이버는 SPA 이동을 스스로 못 잡아 경로가 바뀔 때마다 부른다(GA4는 위 주석대로 자동) */
export function trackPageView() {
  if (naverLoaded && window.wcs) window.wcs_do?.()
}

/** 동의를 거둔다 — 이미 붙은 스크립트는 뗄 수 없어 GA 수집을 끄고 GA 쿠키를 지운 뒤 새로 고친다(새로 고친 화면엔 스크립트가 없다) */
export function withdrawAnalytics() {
  saveConsent('N')
  if (!gaLoaded && !naverLoaded) return
  if (gaLoaded) (window as unknown as Record<string, unknown>)[`ga-disable-${gaLoaded}`] = true
  // GA 쿠키(_ga, _ga_XXXX)는 상위 도메인에 걸리므로 지금 호스트와 그 상위 도메인마다 지운다
  const parts = location.hostname.split('.')
  const domains = parts.map((_, i) => parts.slice(i).join('.')).filter((d) => d.includes('.'))
  for (const c of document.cookie.split(';')) {
    const name = c.split('=')[0].trim()
    if (!name.startsWith('_ga')) continue
    document.cookie = `${name}=; Max-Age=0; path=/`
    for (const d of domains) document.cookie = `${name}=; Max-Age=0; path=/; domain=.${d}`
  }
  location.reload()
}
