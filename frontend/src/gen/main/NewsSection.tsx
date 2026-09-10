import { useEffect, useState } from 'react'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { POST_LIST_URL } from '../../adm/post/post.api'
import type { Post } from '../../adm/post/post.api'
import Reveal from './Reveal'

/**
 * 메인 소식 — 실제 게시판 데이터를 보여준다.
 *
 * <p>어느 게시판을 쓸지는 확장설정 `main.news-board-id`로 정한다(코드에 고정하지 않는 이유:
 * 게시판 구성이 바뀔 수 있고, 취미 게시판은 관리자 화면에서 계속 늘어난다).
 * 값이 비어 있으면 이 섹션을 아예 렌더하지 않는다(GenMain이 판단).
 *
 * <p>조회는 공개 경로다 — `selectPostList.do`는 SecurityConfig permitAll이라 비로그인도 읽는다.
 * 다만 <b>게시판 접근권</b>은 서버(GenAccessGuard)가 따로 본다. 볼 수 없는 게시판이면 403이
 * 오는데, 그때 "등록된 글이 없습니다"를 보여주면 사실과 다르므로 섹션을 감춘다.
 */
export default function NewsSection({
  boardId,
  onNavigate,
}: {
  boardId: string
  onNavigate: (path: string) => void
}) {
  const [rows, setRows] = useState<Post[]>([])
  const [loaded, setLoaded] = useState(false)
  const [denied, setDenied] = useState(false)

  useEffect(() => {
    apiPost<ListResult<Post>>(POST_LIST_URL, { boardId, pageNo: 1, pageSize: 5 })
      .then((res) => setRows(res.list ?? []))
      .catch(() => setDenied(true))
      .finally(() => setLoaded(true))
  }, [boardId])

  if (denied) return null

  return (
    <section className="gen-section is-sub" id="news">
      <div className="gen-wfix gen-news">
        <Reveal dir="left">
          <span className="gen-eyebrow">NOTICE</span>
          <h2 className="gen-sec-title">
            새로운 <span className="gen-point">소식</span>
          </h2>
          <p className="gen-lead">공지와 업데이트를 전해드립니다.</p>
          <div style={{ marginTop: 24 }}>
            <button type="button" className="gen-more-btn" onClick={() => onNavigate(`/gen/board/${boardId}`)}>
              전체 보기
            </button>
          </div>
        </Reveal>

        <Reveal as="ul" dir="right" className="gen-news-list">
          {rows.map((p) => (
            <li key={p.rowId}>
              <button type="button" onClick={() => onNavigate(`/gen/board/${boardId}?post=${p.rowId}`)}>
                <span className="gen-news-subj">{p.title}</span>
                <span className="gen-news-date">{(p.regDt ?? '').slice(0, 10).replaceAll('-', '.')}</span>
              </button>
            </li>
          ))}
          {loaded && rows.length === 0 && (
            <li>
              <div className="gen-news-empty">등록된 글이 없습니다.</div>
            </li>
          )}
        </Reveal>
      </div>
    </section>
  )
}
