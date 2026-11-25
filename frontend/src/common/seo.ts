import { useEffect } from 'react'

/**
 * 화면 메타(CMS 1.2.43) — SPA라 index.html의 description·keywords가 고정이라, 사이트 설정(확장설정 seo.*)과 화면 내용으로 갱신한다.
 *
 * ⚠ 자바스크립트를 실행하는 검색엔진(구글 등)만 이 값을 본다. SNS 공유 미리보기(OG)는 크롤러가 스크립트를 돌리지 않아
 * 서버 렌더링 없이는 바꿀 수 없다(사용자 결정으로 범위 밖) — index.html의 og:* 기본값이 그대로 나간다.
 */

/** 사이트 기본 설명 — 게시글 화면이 자기 요약으로 바꿨다가 나갈 때 이 값으로 되돌린다 */
let siteDescription = ''

function meta(name: string): HTMLMetaElement {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.name = name
    document.head.appendChild(el)
  }
  return el
}

/** 사이트 설명·키워드(확장설정) — 비어 있으면 index.html 기본값을 그대로 둔다 */
export function setSiteMeta(description?: string, keywords?: string) {
  if (description) {
    siteDescription = description
    meta('description').content = description
  }
  if (keywords) meta('keywords').content = keywords
}

/** 본문 HTML → 태그를 지운 평문 앞부분(메타 설명용) */
export function plainExcerpt(html?: string, max = 150): string {
  // ★ DOMParser 문서는 스크립트·이미지 로드가 없는 비활성 문서다. createElement('div').innerHTML은 문서에 붙이지 않아도
  //   <img onerror>가 실행된다 — 본문은 사용자가 쓴 HTML이라 그 길로 쓰면 XSS가 된다
  const doc = new DOMParser().parseFromString(html ?? '', 'text/html')
  const text = (doc.body.textContent ?? '').replace(/\s+/g, ' ').trim()
  return text.length > max ? `${text.slice(0, max)}…` : text
}

/**
 * 화면 하나가 머무는 동안 제목·설명을 바꾸고, 나가면 되돌린다(게시글 상세 등).
 * 제목은 지금 제목(“게시판 | 사이트”) 앞에 붙인다 — 사이트명을 따로 몰라도 된다.
 */
export function usePageMeta(title?: string, description?: string) {
  useEffect(() => {
    if (!title && !description) return
    const prevTitle = document.title
    const prevDesc = meta('description').content
    if (title) document.title = `${title} | ${prevTitle}`
    if (description) meta('description').content = description
    return () => {
      document.title = prevTitle
      meta('description').content = prevDesc || siteDescription
    }
  }, [title, description])
}

/** 게시판 RSS 자동 발견 링크(<link rel="alternate">) — 화면이 머무는 동안만 둔다 */
export function useRssLink(href?: string, title?: string) {
  useEffect(() => {
    if (!href) return
    const el = document.createElement('link')
    el.rel = 'alternate'
    el.type = 'application/rss+xml'
    el.href = href
    if (title) el.title = title
    document.head.appendChild(el)
    return () => {
      el.remove()
    }
  }, [href, title])
}
