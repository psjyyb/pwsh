import { useEffect, useState } from 'react'
import { Button, Card, Col, Empty, Row, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import { fileApi } from '../api/file'
import { apiPost } from '../api/http'
import type { ListResult } from '../api/http'
import { tokenStore } from '../auth/token'
import { popupApi } from '../adm/popup/popup.api'
import type { Popup } from '../adm/popup/popup.api'
import { configApi } from '../adm/config/config.api'
import { hobbyApi } from '../adm/hobby/hobby.api'
import { BBS_LIST_URL } from '../adm/bbs/bbs.api'
import type { Bbs } from '../adm/bbs/bbs.api'
import { recruitApi } from './recruit/recruit.api'
import type { Recruit } from './recruit/recruit.api'

const TEAL = '#00897b'
const STATUS_OPEN = 'RECRUIT01'

interface Category { hobbyId: string; bbsinfoId?: string; name: string; count: number }
interface RecentPost extends Bbs { catName?: string }

/** '오늘 하루 보지 않기' 쿠키(익일 자정 만료) */
function hideToday(popId: string) {
  const t = new Date()
  t.setHours(24, 0, 0, 0)
  document.cookie = `popup_hide_${popId}=Y; expires=${t.toUTCString()}; path=/`
}
function isHidden(popId?: string) {
  return !!popId && document.cookie.split('; ').includes(`popup_hide_${popId}=Y`)
}

/** 개별 팝업 레이어 — top/left/width/height(px)로 위치·크기, 이미지/링크/내용, 닫기·오늘하루안보기 */
function PopupLayer({ popup, onClose }: { popup: Popup; onClose: () => void }) {
  const [img, setImg] = useState('')

  useEffect(() => {
    let url = ''
    let alive = true
    if (popup.fileId) {
      fileApi.imageUrl(popup.fileId).then((u) => { if (alive) { url = u; setImg(u) } else URL.revokeObjectURL(u) }).catch(() => {})
    }
    return () => { alive = false; if (url) URL.revokeObjectURL(url) }
  }, [popup.fileId])

  const w = popup.popWidth || '500'
  const h = popup.popHeight || '400'
  const top = popup.popTop || '100'
  const left = popup.popLeft || '100'

  const href = popup.link
    ? /^(https?:)?\/\//i.test(popup.link) || popup.link.startsWith('/')
      ? popup.link
      : `https://${popup.link}`
    : undefined

  const image = img ? (
    <img src={img} alt={popup.popNm ?? ''} style={{ display: 'block', width: '100%', height: `${h}px`, objectFit: 'contain' }} />
  ) : (
    <div style={{ height: `${h}px`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#bbb' }}>이미지 없음</div>
  )

  return (
    <div
      style={{
        position: 'fixed', top: `${top}px`, left: `${left}px`, width: `${w}px`, zIndex: 1000,
        background: '#fff', border: '1px solid #ccc', boxShadow: '0 2px 12px rgba(0,0,0,.2)',
      }}
    >
      {href ? <a href={href} target="_blank" rel="noreferrer">{image}</a> : image}
      {popup.txt && <div style={{ padding: 8, fontSize: 13 }}>{popup.txt}</div>}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 10px', borderTop: '1px solid #eee', background: '#f7f7f7', fontSize: 13 }}>
        <a onClick={() => { hideToday(popup.dbKey!); onClose() }} style={{ cursor: 'pointer' }}>오늘 하루 보지 않기</a>
        <a onClick={onClose} style={{ cursor: 'pointer' }}>닫기</a>
      </div>
    </div>
  )
}

/**
 * 사용자 메인 — 취미 커뮤니티 랜딩(히어로 · 취미 카테고리 · 지금 모집 중 · 최근 이야기).
 * 취미 카테고리는 GEN "취미게시판" 그룹의 하위 게시판에서 도출(무코드 확장). 등록 팝업도 레이어로 노출.
 */
export default function GenMain() {
  const navigate = useNavigate()
  const [popups, setPopups] = useState<Popup[]>([])
  const [siteTitle, setSiteTitle] = useState('PWSH')
  const [categories, setCategories] = useState<Category[]>([])
  const [recruits, setRecruits] = useState<Recruit[]>([])
  const [posts, setPosts] = useState<RecentPost[]>([])
  const loggedIn = !!tokenStore.get()

  useEffect(() => {
    popupApi.mainList().then((list) => setPopups(list.filter((p) => !isHidden(p.dbKey)))).catch(() => {})
    configApi.view().then((c) => { if (c.title) setSiteTitle(c.title) }).catch(() => {})
    recruitApi.list({ statusCd: STATUS_OPEN, pageIndex: 1, size: 4 })
      .then((r) => setRecruits(r.list)).catch(() => {})

    hobbyApi.listAll().then(async (hobbies) => {
      const cats: Category[] = hobbies.map((h) => ({
        hobbyId: h.dbKey!, bbsinfoId: h.bbsinfoId, name: h.hobbyNm ?? '', count: Number(h.postCnt ?? 0),
      }))
      setCategories(cats)

      // 취미별 연결 게시판의 최신 글 병렬 수집 → 최근 이야기
      const results = await Promise.all(
        cats.filter((c) => c.bbsinfoId).map((c) =>
          apiPost<ListResult<Bbs>>(BBS_LIST_URL, { bbsinfoId: c.bbsinfoId, pageIndex: 1, size: 4 })
            .then((res) => ({ cat: c, res }))
            .catch(() => ({ cat: c, res: { list: [], totCnt: 0 } as ListResult<Bbs> })),
        ),
      )
      const merged: RecentPost[] = results
        .flatMap(({ cat, res }) => res.list.filter((b) => Number(b.bbsDepth ?? 0) === 0).map((b) => ({ ...b, catName: cat.name })))
        .sort((a, b) => (b.regDt ?? '').localeCompare(a.regDt ?? '') || Number(b.dbKey) - Number(a.dbKey))
        .slice(0, 6)
      setPosts(merged)
    }).catch(() => {})
  }, [])

  const close = (dbKey?: string) => setPopups((prev) => prev.filter((p) => p.dbKey !== dbKey))
  const statusTag = (cd?: string, nm?: string) =>
    cd === STATUS_OPEN ? <Tag color="green">{nm ?? '모집중'}</Tag> : <Tag>{nm ?? '마감'}</Tag>

  return (
    <div style={{ maxWidth: 1080, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 28 }}>
      {/* 히어로 */}
      <div style={{ background: '#e6f4f1', borderRadius: 16, padding: '40px 24px', textAlign: 'center' }}>
        <div style={{ fontSize: 28, fontWeight: 700, color: '#004d40', marginBottom: 8 }}>취미로 만나는 사람들</div>
        <div style={{ fontSize: 15, color: '#00695c', marginBottom: 24 }}>
          {siteTitle}에서 등산 · 보드게임 · 낚시… 취미를 나누고, 함께할 사람을 찾아보세요.
        </div>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
          {!loggedIn && <Button type="primary" size="large" onClick={() => navigate('/signup')}>회원가입</Button>}
          <Button size="large" onClick={() => navigate('/gen/recruit')}>모집 둘러보기</Button>
        </div>
      </div>

      {/* 취미 카테고리 */}
      <section>
        <h3 style={{ fontSize: 18, fontWeight: 600, marginBottom: 14 }}>취미 카테고리</h3>
        {categories.length === 0 ? (
          <Empty description="등록된 취미 게시판이 없습니다." />
        ) : (
          <Row gutter={[16, 16]}>
            {categories.map((c) => (
              <Col key={c.bbsinfoId} xs={12} sm={8} md={6}>
                <Card hoverable style={{ textAlign: 'center', borderTop: `3px solid ${TEAL}` }}
                  onClick={() => navigate(`/gen/hobby/${c.hobbyId}`)} styles={{ body: { padding: '20px 12px' } }}>
                  <div style={{ fontSize: 17, fontWeight: 600, color: '#00695c' }}>{c.name}</div>
                  <div style={{ fontSize: 12, color: '#999', marginTop: 6 }}>글 {c.count}</div>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </section>

      {/* 지금 모집 중 */}
      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 14 }}>
          <h3 style={{ fontSize: 18, fontWeight: 600, margin: 0 }}>지금 모집 중</h3>
          <a style={{ color: TEAL }} onClick={() => navigate('/gen/recruit')}>전체 보기</a>
        </div>
        {recruits.length === 0 ? (
          <Empty description="진행 중인 모집이 없습니다." />
        ) : (
          <Row gutter={[16, 16]}>
            {recruits.map((r) => (
              <Col key={r.dbKey} xs={24} sm={12}>
                <Card hoverable onClick={() => navigate('/gen/recruit')} styles={{ body: { padding: 16 } }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <Tag color="cyan">{r.hobbyNm}</Tag>
                    {statusTag(r.statusCd, r.statusNm)}
                  </div>
                  <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 8 }}>{r.title}</div>
                  <div style={{ fontSize: 13, color: '#888', display: 'flex', gap: 14, flexWrap: 'wrap' }}>
                    <span>📍 {r.region || '-'}</span>
                    <span>🗓 {r.meetDt || '-'}</span>
                    <span>👥 {r.acceptedCnt ?? 0}{r.capacity ? ` / ${r.capacity}` : ''}</span>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </section>

      {/* 최근 이야기 */}
      <section>
        <h3 style={{ fontSize: 18, fontWeight: 600, marginBottom: 14 }}>최근 이야기</h3>
        {posts.length === 0 ? (
          <Empty description="등록된 글이 없습니다." />
        ) : (
          <Card styles={{ body: { padding: 0 } }}>
            {posts.map((p, i) => (
              <div key={p.dbKey}
                onClick={() => navigate(`/gen/board/${p.bbsinfoId}`)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', cursor: 'pointer',
                  borderTop: i === 0 ? 'none' : '1px solid #f0f0f0',
                }}>
                <Tag color="cyan" style={{ flexShrink: 0 }}>{p.catName}</Tag>
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.title}</span>
                {Number(p.commentCnt) > 0 && <span style={{ color: TEAL, flexShrink: 0 }}>[{p.commentCnt}]</span>}
                <span style={{ fontSize: 12, color: '#aaa', flexShrink: 0 }}>{p.regNm || p.regId} · {p.regDt}</span>
              </div>
            ))}
          </Card>
        )}
      </section>

      {popups.map((p) => (
        <PopupLayer key={p.dbKey} popup={p} onClose={() => close(p.dbKey)} />
      ))}
    </div>
  )
}
