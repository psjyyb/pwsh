import type { CSSProperties } from 'react'
import DOMPurify from 'dompurify'

interface Props {
  html: string
  className?: string
  style?: CSSProperties
}

/**
 * 서버/에디터가 준 HTML을 DOMPurify로 새니타이즈한 뒤 렌더하는 단일 지점.
 * 저장형 XSS 방지 — 본문/페이지 HTML은 `dangerouslySetInnerHTML`을 직접 쓰지 말고 이 컴포넌트를 쓴다.
 */
export default function SafeHtml({ html, className, style }: Props) {
  const clean = DOMPurify.sanitize(html ?? '')
  return <div className={className} style={style} dangerouslySetInnerHTML={{ __html: clean }} />
}
