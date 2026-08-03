import { useEffect, useState } from 'react'
import { Button, Card, Col, Empty, Row, Tag, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { fileApi } from '../api/file'
import { apiPost } from '../api/http'
import type { ListResult } from '../api/http'
import { popupApi } from '../adm/popup/popup.api'
import type { Popup } from '../adm/popup/popup.api'
import { hobbyApi, userHobbyApi } from '../adm/hobby/hobby.api'
import { tokenStore } from '../auth/token'
import { BBS_LIST_URL } from '../adm/bbs/bbs.api'
import type { Bbs } from '../adm/bbs/bbs.api'
import { recruitApi } from './recruit/recruit.api'
import type { Recruit } from './recruit/recruit.api'
import { gen, cardGradients } from './theme'

const TEAL = gen.primary
const STATUS_OPEN = 'RECRUIT01'

interface Category { hobbyId: string; bbsinfoId?: string; name: string; count: number; thumbId?: string; difficultyNm?: string; summary?: string; memberCnt?: number }
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
  const [categories, setCategories] = useState<Category[]>([])
  const [recruits, setRecruits] = useState<Recruit[]>([])
  const [posts, setPosts] = useState<RecentPost[]>([])
  const loggedIn = !!tokenStore.get()
  const [myIds, setMyIds] = useState<Set<string>>(new Set()) // 내가 담은 취미 id

  useEffect(() => {
    popupApi.mainList().then((list) => setPopups(list.filter((p) => !isHidden(p.dbKey)))).catch(() => {})
    if (loggedIn) userHobbyApi.list().then((l) => setMyIds(new Set(l.map((u) => u.hobbyId!).filter(Boolean)))).catch(() => {})
    recruitApi.list({ statusCd: STATUS_OPEN, pageIndex: 1, size: 4 })
      .then((r) => setRecruits(r.list)).catch(() => {})

    hobbyApi.listAll().then(async (hobbies) => {
      const cats: Category[] = hobbies.map((h) => ({
        hobbyId: h.dbKey!, bbsinfoId: h.bbsinfoId, name: h.hobbyNm ?? '', count: Number(h.postCnt ?? 0),
        thumbId: h.thumbId, difficultyNm: h.difficultyNm, summary: h.summary, memberCnt: Number(h.memberCnt ?? 0),
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

  const toggleMy = async (hobbyId: string) => {
    const has = myIds.has(hobbyId)
    try {
      if (has) await userHobbyApi.remove(hobbyId)
      else await userHobbyApi.save(hobbyId)
      setMyIds((prev) => { const n = new Set(prev); if (has) n.delete(hobbyId); else n.add(hobbyId); return n })
      message.success(has ? '담기를 취소했습니다.' : '내 취미에 담았습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '처리 실패')
    }
  }
  const statusTag = (cd?: string, nm?: string) =>
    cd === STATUS_OPEN ? <Tag color="green">{nm ?? '모집중'}</Tag> : <Tag>{nm ?? '마감'}</Tag>

  return (
    <div style={{ maxWidth: 1080, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 28 }}>
      {/* 히어로 */}
      <div style={{ background: gen.heroTint, borderRadius: 24, padding: '36px 24px', textAlign: 'center' }}>
        <div aria-hidden style={{ fontSize: 26, marginBottom: 8 }}>✨💜⭐️</div>
        <div style={{ fontSize: 26, fontWeight: 800, color: gen.heroText, marginBottom: 8, letterSpacing: '-.5px' }}>취미로 만나는 사람들</div>
        <div style={{ fontSize: 15, color: '#7A72A8', marginBottom: 22 }}>
          관심사가 같은 사람들과 이야기하고, 함께할 사람을 찾아보세요.
        </div>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
          {!loggedIn && (
            <Button type="primary" size="large" onClick={() => navigate('/signup')}
              style={{ borderRadius: 16, fontWeight: 700, paddingInline: 28 }}>회원가입</Button>
          )}
          <Button size="large" onClick={() => navigate('/gen/recruit')}
            style={{ borderRadius: 16, paddingInline: 24 }}>모집 둘러보기</Button>
        </div>
      </div>

      {/* 취미 목록(유닛형 컬러 카드) */}
      <section>
        <h3 style={{ fontSize: 22, fontWeight: 800, color: gen.heroText, margin: '0 0 16px' }}>어떤 취미부터 시작할까?</h3>
        {categories.length === 0 ? (
          <Empty description="등록된 취미가 없습니다." />
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 400px), 1fr))', gap: 12 }}>
            {categories.map((c, i) => (
              <div key={c.hobbyId} onClick={() => navigate(`/gen/hobby/${c.hobbyId}`)} className="gen-hobby-card"
                style={{ background: cardGradients[i % cardGradients.length], borderRadius: 22, cursor: 'pointer', color: '#fff', boxShadow: '0 6px 18px rgba(108,78,227,.18)', position: 'relative', overflow: 'hidden' }}>
                {/* 상단 광택 */}
                <div aria-hidden style={{ position: 'absolute', inset: 0, background: 'radial-gradient(120% 90% at 0% 0%, rgba(255,255,255,.25), transparent 58%)', pointerEvents: 'none' }} />
                {/* 배경 워터마크 — 우측에 작은 라운드 사각형(썸네일)/이니셜. 버튼에 가려져도 무방 */}
                {c.thumbId
                  ? <img aria-hidden src={`/api/pub/image/${c.thumbId}`} alt="" style={{ position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%) rotate(15deg)', width: 66, height: 66, objectFit: 'cover', borderRadius: 16, opacity: 0.18, pointerEvents: 'none' }} />
                  : <span aria-hidden style={{ position: 'absolute', right: 28, bottom: -24, fontSize: 96, fontWeight: 800, color: 'rgba(255,255,255,.13)', lineHeight: 1, pointerEvents: 'none' }}>{c.name.slice(0, 1)}</span>}
                <div style={{ position: 'relative', zIndex: 1, display: 'flex', alignItems: 'center', gap: 16, padding: '20px 22px' }}>
                  <div style={{ width: 54, height: 54, borderRadius: 17, background: 'rgba(255,255,255,.24)', boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.35)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 23, fontWeight: 800, flexShrink: 0, overflow: 'hidden' }}>
                    {c.thumbId
                      ? <img src={`/api/pub/image/${c.thumbId}`} alt={c.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      : c.name.slice(0, 1)}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 18, fontWeight: 700 }}>{c.name}</div>
                    {c.summary && (
                      <div style={{ fontSize: 12.5, opacity: 0.92, marginTop: 3, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.summary}</div>
                    )}
                    <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                      {c.difficultyNm && (
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'rgba(255,255,255,.22)', borderRadius: 999, padding: '3px 10px', fontSize: 12, fontWeight: 600 }}>⭐ {c.difficultyNm}</span>
                      )}
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'rgba(255,255,255,.22)', borderRadius: 999, padding: '3px 10px', fontSize: 12, fontWeight: 600 }}>👥 {c.memberCnt ?? 0}</span>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'rgba(255,255,255,.22)', borderRadius: 999, padding: '3px 10px', fontSize: 12, fontWeight: 600 }}>📝 글 {c.count}</span>
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}>
                    {loggedIn && (
                      <button type="button" className="gen-heart" aria-label={myIds.has(c.hobbyId) ? '담기 취소' : '내 취미 담기'}
                        onClick={(e) => { e.stopPropagation(); toggleMy(c.hobbyId) }}
                        style={{ width: 34, height: 34, borderRadius: '50%', border: 'none', cursor: 'pointer', background: 'rgba(255,255,255,.22)', color: '#fff', fontSize: 17, lineHeight: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        {myIds.has(c.hobbyId) ? '♥' : '♡'}
                      </button>
                    )}
                    <span aria-hidden className="gen-hobby-go" style={{ width: 34, height: 34, borderRadius: '50%', background: 'rgba(255,255,255,.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, flexShrink: 0 }}>›</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* 지금 모집 중 */}
      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 14 }}>
          <h3 style={{ fontSize: 20, fontWeight: 800, margin: 0, color: gen.heroText }}>🔥 지금 모집 중</h3>
          <a style={{ color: TEAL }} onClick={() => navigate('/gen/recruit')}>전체 보기</a>
        </div>
        {recruits.length === 0 ? (
          <Empty description="진행 중인 모집이 없습니다." />
        ) : (
          <Row gutter={[16, 16]}>
            {recruits.map((r) => (
              <Col key={r.dbKey} xs={24} sm={12}>
                <Card hoverable onClick={() => navigate(`/gen/recruit/${r.dbKey}`)} styles={{ body: { padding: 16 } }}>
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
        <h3 style={{ fontSize: 20, fontWeight: 800, marginBottom: 14, color: gen.heroText }}>💬 최근 이야기</h3>
        {posts.length === 0 ? (
          <Empty description="등록된 글이 없습니다." />
        ) : (
          <Card styles={{ body: { padding: 0 } }}>
            {posts.map((p, i) => (
              <div key={p.dbKey}
                onClick={() => navigate(`/gen/board/${p.bbsinfoId}?post=${p.dbKey}`)}
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
