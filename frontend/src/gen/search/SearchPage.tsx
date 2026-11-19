import { useEffect, useRef, useState } from 'react'
import { AutoComplete, Card, Empty, Input, Spin, Tag } from 'antd'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { SEARCH_PREVIEW_SIZE, searchApi } from '../../api/search'
import type { SearchResult } from '../../api/search'
import { gen } from '../theme'
import { PageBody, PageHead } from '../../common/gen/components/PageShell'

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

/** 자동완성 조회 간격 — 글자마다 서버를 부르지 않게 */
const SUGGEST_DELAY_MS = 200

/**
 * 통합 검색(/gen/search?q=) — 취미·모집·게시글을 묶어 표시.
 * 검색어는 주소(q)에 둔다 — 헤더 검색 아이콘·인기 검색어·교정 제안·뒤로가기가 모두 같은 경로를 탄다.
 */
export default function SearchPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const q = (params.get('q') ?? '').trim()
  const [data, setData] = useState<SearchResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [popular, setPopular] = useState<string[]>([])
  const [input, setInput] = useState(q)
  const [options, setOptions] = useState<{ value: string }[]>([])
  const suggestTimer = useRef<number>()
  const suggestSeq = useRef(0)
  const goTimer = useRef<number>()

  // 인기 검색어(CMS 1.2.38) — 실패해도 검색은 그대로(목록만 안 보인다)
  useEffect(() => {
    searchApi.popular().then(setPopular).catch(() => setPopular([]))
  }, [])

  // 인기 검색어·교정 제안·뒤로가기로 주소(q)가 바뀌면 입력칸도 그 말로
  useEffect(() => {
    setInput(q)
  }, [q])

  useEffect(() => () => {
    window.clearTimeout(suggestTimer.current)
    window.clearTimeout(goTimer.current)
  }, [])

  // 자동완성(CMS 1.2.39) — 늦게 온 이전 응답이 최신 목록을 덮지 않게 순번으로 거른다. 실패하면 목록만 비운다
  const changeInput = (v: string) => {
    setInput(v)
    window.clearTimeout(suggestTimer.current)
    const seq = ++suggestSeq.current
    if (!v.trim()) {
      setOptions([])
      return
    }
    suggestTimer.current = window.setTimeout(() => {
      searchApi.suggest(v)
        .then((list) => { if (seq === suggestSeq.current) setOptions(list.map((s) => ({ value: s }))) })
        .catch(() => { if (seq === suggestSeq.current) setOptions([]) })
    }, SUGGEST_DELAY_MS)
  }

  // 목록에서 Enter로 고르면 검색창의 Enter(입력한 말)가 먼저, 고른 말이 바로 뒤에 같은 틱으로 들어온다.
  // 둘 다 주소에 넣으면 기록이 두 번 쌓이고 순서가 바뀌면 입력한 말로 검색된다 — 같은 틱에선 마지막 것만 쓴다
  const go = (v: string) => {
    window.clearTimeout(suggestTimer.current)
    suggestSeq.current++
    setOptions([])
    window.clearTimeout(goTimer.current)
    goTimer.current = window.setTimeout(() => {
      const kw = v.trim()
      navigate(kw ? `/gen/search?q=${encodeURIComponent(kw)}` : '/gen/search')
    }, 0)
  }

  useEffect(() => {
    if (!q) { setData(null); return }
    setLoading(true)
    searchApi.all(q).then(setData).catch(() => setData(null)).finally(() => setLoading(false))
  }, [q])

  const rowStyle = { padding: '10px 14px', cursor: 'pointer', borderTop: '1px solid var(--gen-line)' } as const
  // ★ 건수는 배열 길이가 아니라 서버가 센 값을 쓴다 — 목록은 유형별 10건까지만 온다
  const total = data ? data.hobbyCount + data.recruitCount + data.postCount + data.pageCount : 0

  /** 10건을 넘겨 잘린 유형에만 붙는 안내 — 안 붙이면 "왜 10개뿐이지?"가 된다 */
  const more = (count: number) =>
    count > SEARCH_PREVIEW_SIZE ? (
      <div style={{ padding: '8px 14px', fontSize: 12, color: '#999', borderTop: '1px solid var(--gen-line)' }}>
        상위 {SEARCH_PREVIEW_SIZE}건만 표시했습니다. 검색어를 더 구체적으로 입력해 보세요.
      </div>
    ) : null

  return (
    <>
      <PageHead
        eyebrow="검색"
        title={q ? <>‘{q}’ 검색 결과</> : '검색어를 입력하세요'}
        lead="취미·모집·게시글을 한 번에 찾습니다."
        right={q && total > 0
          ? <span className="gen-nums" style={{ fontSize: 13, fontWeight: 600, color: 'var(--gen-ink-soft)' }}>결과 {total}건</span>
          : undefined}
      />
      <PageBody narrow>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      {/* 헤더 검색 아이콘은 이 화면으로 오기만 한다 — 입력은 여기서 받는다 */}
      <AutoComplete
        value={input}
        options={options}
        onChange={changeInput}
        onSelect={(v: string) => go(v)}
        // 첫 항목을 미리 잡아 두면 Enter가 입력한 말 대신 그 항목을 고른다 — 고르려면 화살표로 내려간다
        defaultActiveFirstOption={false}
        style={{ width: '100%' }}
      >
        <Input.Search size="large" placeholder="취미·모집·게시글 검색" allowClear autoFocus={!q} onSearch={go} />
      </AutoComplete>
      {popular.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 6 }}>
          <span style={{ color: 'var(--gen-ink-soft)', fontSize: 13, marginRight: 4 }}>인기 검색어</span>
          {popular.map((p, i) => (
            <Tag key={p} style={{ cursor: 'pointer', marginInlineEnd: 0 }}
              onClick={() => navigate(`/gen/search?q=${encodeURIComponent(p)}`)}>
              {i + 1}. {p}
            </Tag>
          ))}
        </div>
      )}
      {loading ? (
        <div style={{ textAlign: 'center', padding: 60 }}><Spin /></div>
      ) : !q ? null : total === 0 ? (
        <Empty
          description={
            <>
              <div>검색 결과가 없습니다.</div>
              {/* 오타 교정(CMS 1.2.39) — 서버가 0건일 때만 준다. 바꿔 찾지 않고 제안만 한다 */}
              {data?.suggest && (
                <div style={{ marginTop: 8 }}>
                  혹시 <a onClick={() => navigate(`/gen/search?q=${encodeURIComponent(data.suggest!)}`)}>‘{data.suggest}’</a>을(를) 찾으셨나요?
                </div>
              )}
            </>
          }
        />
      ) : (
        <>
          {data!.hobbies.length > 0 && (
            <Card title={`취미 (${data!.hobbyCount})`} styles={{ body: { padding: 0 } }} style={{ borderRadius: 16 }}>
              {data!.hobbies.map((h) => (
                <div key={h.rowId} style={rowStyle} onClick={() => navigate(`/gen/hobby/${h.rowId}`)}>
                  <span style={{ fontWeight: 600 }}>{h.hobbyName}</span>
                  {h.difficultyName && <Tag color="purple" style={{ marginLeft: 8 }}>{h.difficultyName}</Tag>}
                  {h.summary && <div style={{ fontSize: 13, color: '#888', marginTop: 2 }}>{h.summary}</div>}
                </div>
              ))}
              {more(data!.hobbyCount)}
            </Card>
          )}
          {data!.recruits.length > 0 && (
            <Card title={`모집 (${data!.recruitCount})`} styles={{ body: { padding: 0 } }} style={{ borderRadius: 16 }}>
              {data!.recruits.map((r) => (
                <div key={r.rowId} style={rowStyle} onClick={() => navigate(`/gen/recruit/${r.rowId}`)}>
                  <Tag color="cyan">{r.hobbyName}</Tag>
                  <span style={{ fontWeight: 600 }}>{r.title}</span>
                  <span style={{ marginLeft: 8, fontSize: 12, color: '#999' }}>
                    {r.region || ''} {r.statusCd === 'RECRUIT01' ? '· 모집중' : '· 마감'}
                  </span>
                </div>
              ))}
              {more(data!.recruitCount)}
            </Card>
          )}
          {data!.posts.length > 0 && (
            <Card title={`게시글 (${data!.postCount})`} styles={{ body: { padding: 0 } }} style={{ borderRadius: 16 }}>
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
              {more(data!.postCount)}
            </Card>
          )}
          {/* 안내페이지 — 메뉴에 연결되고 열람 권한이 있는 것만 서버가 내려준다 */}
          {data!.pages.length > 0 && (
            <Card title={`안내 (${data!.pageCount})`} styles={{ body: { padding: 0 } }} style={{ borderRadius: 16 }}>
              {data!.pages.map((g) => (
                <div key={g.rowId} style={rowStyle} onClick={() => navigate(`/gen/page/${g.rowId}`)}>
                  <Tag color="geekblue">안내</Tag>
                  <span style={{ fontWeight: 600 }}>{g.title}</span>
                  {g.searchSnippet && (
                    <div style={{ fontSize: 13, color: gen.inkSoft, marginTop: 4, lineHeight: 1.5 }}>
                      {highlight(g.searchSnippet, q)}
                    </div>
                  )}
                </div>
              ))}
              {more(data!.pageCount)}
            </Card>
          )}
        </>
      )}
      </div>
      </PageBody>
    </>
  )
}
