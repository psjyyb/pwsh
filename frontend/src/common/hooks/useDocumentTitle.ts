import { useEffect } from 'react'
import { setBaseTitle } from '../seo'

/**
 * 문서 제목을 `[페이지명 | 사이트명]` 형태로 설정한다.
 * SPA는 라우팅해도 index.html의 <title>이 고정이라, 화면/레이아웃에서 이 훅으로 갱신한다.
 * pageTitle이 없으면 사이트명만 노출. siteTitle은 환경설정(config.title)에서 주입.
 * document.title을 직접 쓰지 않고 seo.ts에 기본 제목으로 넘긴다(CMS 1.2.45, 이 저장소 v0.6.45) — 게시글 상세가 앞에 붙인 제목을
 * 늦게 도착한 사이트명·메뉴명이 덮지 않게.
 */
export function useDocumentTitle(pageTitle?: string, siteTitle = 'Framework') {
  useEffect(() => {
    setBaseTitle(pageTitle ? `${pageTitle} | ${siteTitle}` : siteTitle)
  }, [pageTitle, siteTitle])
}
