import { useEffect, useState } from 'react'
import { Card, Empty, Spin, Tag } from 'antd'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { searchApi } from '../../api/search'
import type { SearchResult } from '../../api/search'
import { gen } from '../theme'

/**
 * 스니펫에서 검색어만 강조. dangerouslySetInnerHTML을 쓰지 않고 조각으로 나눠 렌더한다
 * (본문 발췌가 그대로 들어오므로 HTML로 해석시키면 XSS 통로가 된다).
 */
function highlight(text: string, keyword: string) {
  if (!keyword) return text
  const parts: (string | JSX.Element)[] = []
  let rest = text
  let i = 0
  // 대소문자 무시하고 찾되, 출력은 원문 조각을 그대로 쓴다
  for (;;) {
    const at = rest.toLowerCase().indexOf(keyword.toLowerCase())
    if (at < 0) { parts.push(rest); break }
    if (at > 0) parts.push(rest.slice(0, at))
    parts.push(
      <mark key={i++} style={{ background: '#FFF1A8', padding: '0 1px', borderRadius: 3 }}>
        {rest.slice(at, at + keyword.length)}
      </mark>,
    )
    rest = rest.slice(at + keyword.length)
  }
  return <>{parts}</>
}

/** 통합 검색 결과(/gen/search?q=) — 취미·모집·게시글을 묶어 표시. */
export default function SearchPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const q = (params.get('q') ?? '').trim()
  const [data, setData] = useState<SearchResult | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!q) { setData(null); return }
    setLoading(true)
    searchApi.all(q).then(setData).catch(() => setData(null)).finally(() => setLoading(false))
  }, [q])

  const rowStyle = { padding: '10px 14px', cursor: 'pointer', borderTop: '1px solid #f0f0f0' } as const
  const total = data ? data.hobbies.length + data.recruits.length + data.posts.length : 0

  return (
    <div style={{ maxWidth: 860, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div>
        <div style={{ fontSize: 13, color: gen.primary, fontWeight: 700 }}>🔍 통합 검색</div>
        <h2 style={{ fontSize: 22, fontWeight: 800, color: gen.heroText, margin: '4px 0 0' }}>
          {q ? <>‘{q}’ 검색 결과</> : '검색어를 입력하세요'}
        </h2>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 60 }}><Spin /></div>
      ) : !q ? null : total === 0 ? (
        <Empty description="검색 결과가 없습니다." />
      ) : (
        <>
          {data!.hobbies.length > 0 && (
            <Card title={`취미 (${data!.hobbies.length})`} styles={{ body: { padding: 0 } }} style={{ borderRadius: 16 }}>
              {data!.hobbies.map((h) => (
                <div key={h.rowId} style={rowStyle} onClick={() => navigate(`/gen/hobby/${h.rowId}`)}>
                  <span style={{ fontWeight: 600 }}>{h.hobbyName}</span>
                  {h.difficultyName && <Tag color="purple" style={{ marginLeft: 8 }}>{h.difficultyName}</Tag>}
                  {h.summary && <div style={{ fontSize: 13, color: '#888', marginTop: 2 }}>{h.summary}</div>}
                </div>
              ))}
            </Card>
          )}
          {data!.recruits.length > 0 && (
            <Card title={`모집 (${data!.recruits.length})`} styles={{ body: { padding: 0 } }} style={{ borderRadius: 16 }}>
              {data!.recruits.map((r) => (
                <div key={r.rowId} style={rowStyle} onClick={() => navigate(`/gen/recruit/${r.rowId}`)}>
                  <Tag color="cyan">{r.hobbyName}</Tag>
                  <span style={{ fontWeight: 600 }}>{r.title}</span>
                  <span style={{ marginLeft: 8, fontSize: 12, color: '#999' }}>
                    {r.region || ''} {r.statusCd === 'RECRUIT01' ? '· 모집중' : '· 마감'}
                  </span>
                </div>
              ))}
            </Card>
          )}
          {data!.posts.length > 0 && (
            <Card title={`게시글 (${data!.posts.length})`} styles={{ body: { padding: 0 } }} style={{ borderRadius: 16 }}>
              {data!.posts.map((p) => (
                <div key={p.rowId} style={rowStyle} onClick={() => navigate(`/gen/board/${p.boardId}?post=${p.rowId}`)}>
                  <Tag>{p.boardName}</Tag>
                  <span style={{ fontWeight: 600 }}>{p.title}</span>
                  <span style={{ marginLeft: 8, fontSize: 12, color: '#aaa' }}>{p.regName} · {p.regDt}</span>
                  {/* 제목이 아니라 본문에서 걸린 글 — 어디서 걸렸는지 보여준다 */}
                  {p.searchSnippet && (
                    <div style={{ fontSize: 13, color: gen.inkSoft, marginTop: 4, lineHeight: 1.5 }}>
                      {highlight(p.searchSnippet, q)}
                    </div>
                  )}
                </div>
              ))}
            </Card>
          )}
        </>
      )}
    </div>
  )
}
