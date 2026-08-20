import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Card, Spin } from 'antd'
import { pageApi } from '../adm/page/page.api'
import type { Page } from '../adm/page/page.api'
import SafeHtml from '../common/SafeHtml'

/**
 * 범용 페이지 뷰어 — 메뉴(conn_cd=페이지)의 conn_id(page_id)로 page를 조회해 본문(HTML) 렌더.
 * 페이지관리에서 만든 콘텐츠가 메뉴 연결만으로 노출됨(코드 수정 불필요).
 */
export default function GenPageView() {
  const { pageId } = useParams()
  const [page, setPage] = useState<Page | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!pageId) return
    setLoading(true)
    pageApi
      .view(pageId)
      .then(setPage)
      .catch(() => setPage(null))
      .finally(() => setLoading(false))
  }, [pageId])

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />
  if (!page) return <Card>페이지를 찾을 수 없습니다.</Card>

  return (
    <Card title={page.title}>
      {/* 페이지관리(에디터) 작성 HTML — DOMPurify 새니타이즈 후 렌더 */}
      <SafeHtml html={page.context ?? ''} />
    </Card>
  )
}
