import { useEffect } from 'react'

/**
 * 문서 제목을 `[페이지명 | 사이트명]` 형태로 설정한다.
 * SPA는 라우팅해도 index.html의 <title>이 고정이라, 화면/레이아웃에서 이 훅으로 갱신한다.
 * pageTitle이 없으면 사이트명만 노출. siteTitle은 환경설정(t_config.title)에서 주입.
 */
export function useDocumentTitle(pageTitle?: string, siteTitle = 'Framework') {
  useEffect(() => {
    document.title = pageTitle ? `${pageTitle} | ${siteTitle}` : siteTitle
  }, [pageTitle, siteTitle])
}
