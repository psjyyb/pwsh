import { useEffect } from 'react'

/**
 * 화면 메타(CMS 1.2.43) — SPA라 index.html의 description·keywords가 고정이라, 사이트 설정(확장설정 seo.*)과 화면 내용으로 갱신한다.
 *
 * ⚠ 자바스크립트를 실행하는 검색엔진(구글 등)만 이 값을 본다. SNS 공유 미리보기(OG)는 크롤러가 스크립트를 돌리지 않아
 * 서버 렌더링 없이는 바꿀 수 없다(사용자 결정으로 범위 밖) — index.html의 og:* 기본값이 그대로 나간다.
 */

/*
  ★ 제목·설명은 이 모듈이 "기본값 + 화면이 잠시 덮은 값"을 합쳐서 쓴다(CMS 1.2.45 — 이 저장소는 v0.6.45에 먼저 넣음).
  CMS 1.2.43(이 저장소 v0.6.43)에는 레이아웃(useDocumentTitle·setSiteMeta)과 게시글 화면(usePageMeta)이 document.title·meta를 각자 직접 썼다 —
  게시글 주소로 처음 들어와 사이트명·메뉴·확장설정 조회가 글보다 늦게 끝나면 레이아웃이 글 제목·요약을 덮었다
  (검색엔진이 주소를 바로 여는 경우가 바로 그 상황이다). 이제 어느 쪽이 나중에 와도 같은 결과가 된다.
*/

/** 레이아웃이 정한 기본 제목("메뉴 | 사이트") */
let baseTitle = ''
/** 화면(게시글 상세 등)이 잠시 앞에 붙인 제목 */
let pageTitle: string | undefined
/** 사이트 기본 설명(확장설정 seo.description) */
let siteDescription = ''
/** 화면이 잠시 덮은 설명 */
let pageDescription: string | undefined
/** index.html에 적힌 처음 설명 — 확장설정이 비었을 때 되돌릴 값(처음 한 번 읽는다) */
let initialDescription: string | undefined

function meta(name: string): HTMLMetaElement {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.name = name
    document.head.appendChild(el)
  }
  return el
}

function applyTitle() {
  if (pageTitle) document.title = baseTitle ? `${pageTitle} | ${baseTitle}` : pageTitle
  else if (baseTitle) document.title = baseTitle
}

function applyDescription() {
  const el = meta('description')
  if (initialDescription === undefined) initialDescription = el.content
  el.content = pageDescription || siteDescription || initialDescription
}

/** 레이아웃의 기본 제목 — 화면이 덮은 제목이 있으면 그 뒤에 붙는다(useDocumentTitle이 부른다) */
export function setBaseTitle(title: string) {
  baseTitle = title
  applyTitle()
}

/** 사이트 설명·키워드(확장설정) — 비어 있으면 index.html 기본값을 그대로 둔다. 화면이 덮은 설명이 있으면 그쪽이 이긴다 */
export function setSiteMeta(description?: string, keywords?: string) {
  if (description) {
    siteDescription = description
    applyDescription()
  }
  if (keywords) meta('keywords').content = keywords
}

/** 화면 제목·설명을 잠시 덮는다(한 번에 한 화면 — 게시글 상세). 빈 값은 덮지 않는다 */
export function setPageMeta(title?: string, description?: string) {
  pageTitle = title || undefined
  pageDescription = description || undefined
  applyTitle()
  applyDescription()
}

/** 덮은 제목·설명을 거두고 기본값으로 */
export function clearPageMeta() {
  setPageMeta(undefined, undefined)
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
 * 제목은 레이아웃의 기본 제목(“게시판 | 사이트”) 앞에 붙는다 — 기본 제목이 나중에 바뀌어도(늦게 온 사이트명) 앞에 붙은 채로 유지된다.
 */
export function usePageMeta(title?: string, description?: string) {
  useEffect(() => {
    if (!title && !description) return
    setPageMeta(title, description)
    return clearPageMeta
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
