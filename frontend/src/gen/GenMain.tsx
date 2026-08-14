import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Card, Col, Empty, Row, Tag, message } from 'antd'
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
import { gen, hobbyColor } from './theme'

const TEAL = gen.primary
const STATUS_OPEN = 'RECRUIT01'

interface Category { hobbyId: string; bbsinfoId?: string; name: string; count: number; thumbId?: string; difficultyCd?: string; difficultyNm?: string; summary?: string; memberCnt?: number }
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
        <a onClick={() => { hideToday(popup.rowId!); onClose() }} style={{ cursor: 'pointer' }}>오늘 하루 보지 않기</a>
        <a onClick={onClose} style={{ cursor: 'pointer' }}>닫기</a>
      </div>
    </div>
  )
}

/**
 * 섹션 헤더 — 분류 라벨(eyebrow) + 제목 + 우측 보조.
 * 이모지를 섹션 마커로 쓰지 않는다(어느 사이트에나 있는 기본값이라 성격이 드러나지 않는다).
 * 대신 작은 대비 라벨로 분류를 표시하고 제목이 문장 역할을 한다.
 */
function Meta({ label, value }: { label: string; value: string }) {
  return (
    <span style={{ display: 'inline-flex', flexDirection: 'column', lineHeight: 1.35 }}>
      <span style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: '0.1em', color: gen.inkFaint }}>{label}</span>
      <span className="gen-nums" style={{ fontSize: 13, color: gen.inkSoft, fontWeight: 600 }}>{value}</span>
    </span>
  )
}

function SectionHead({ eyebrow, title, right }: { eyebrow: string; title: string; right?: ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 12, marginBottom: 14 }}>
      <div>
        <div className="gen-eyebrow">{eyebrow}</div>
        <h2 className="gen-h1" style={{ color: gen.heroText }}>{title}</h2>
      </div>
      {right}
    </div>
  )
}

/**
 * 사용자 메인 — 취미 커뮤니티 랜딩(취미 도감 · 지금 모집 중 · 이번 주 베스트 · 최근 이야기).
 * 취미 도감은 t_hobby에서 도출(무코드 확장). 등록 팝업도 레이어로 노출.
 */
export default function GenMain() {
  const navigate = useNavigate()
  const [popups, setPopups] = useState<Popup[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [recruits, setRecruits] = useState<Recruit[]>([])
  const [posts, setPosts] = useState<RecentPost[]>([])
  const [best, setBest] = useState<Bbs[]>([]) // 이번 주 베스트(참여도 상위)
  const loggedIn = !!tokenStore.get()
  const [myIds, setMyIds] = useState<Set<string>>(new Set()) // 내가 담은 취미 id

  useEffect(() => {
    popupApi.mainList().then((list) => setPopups(list.filter((p) => !isHidden(p.rowId)))).catch(() => {})
    if (loggedIn) userHobbyApi.list().then((l) => setMyIds(new Set(l.map((u) => u.hobbyId!).filter(Boolean)))).catch(() => {})
    recruitApi.list({ statusCd: STATUS_OPEN, pageIndex: 1, size: 4 })
      .then((r) => setRecruits(r.list)).catch(() => {})
    apiPost<Bbs[]>('/adm/bbs/selectBbsListWeeklyBest.do', {}).then(setBest).catch(() => {})

    hobbyApi.listAll().then(async (hobbies) => {
      const cats: Category[] = hobbies.map((h) => ({
        hobbyId: h.rowId!, bbsinfoId: h.bbsinfoId, name: h.hobbyNm ?? '', count: Number(h.postCnt ?? 0),
        thumbId: h.thumbId, difficultyCd: h.difficultyCd, difficultyNm: h.difficultyNm,
        summary: h.summary, memberCnt: Number(h.memberCnt ?? 0),
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
        .sort((a, b) => (b.regDt ?? '').localeCompare(a.regDt ?? '') || Number(b.rowId) - Number(a.rowId))
        .slice(0, 6)
      setPosts(merged)
    }).catch(() => {})
  }, [])

  const close = (rowId?: string) => setPopups((prev) => prev.filter((p) => p.rowId !== rowId))

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
    <div style={{ maxWidth: 'var(--gen-w-wide)', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 28 }}>
      {/* 히어로 */}
      {/*<div style={{ background: gen.heroTint, borderRadius: 24, padding: '36px 24px', textAlign: 'center' }}>*/}
      {/*  <div aria-hidden style={{ fontSize: 26, marginBottom: 8 }}>✨💜⭐️</div>*/}
      {/*  <div style={{ fontSize: 26, fontWeight: 800, color: gen.heroText, marginBottom: 8, letterSpacing: '-.5px' }}>취미로 만나는 사람들</div>*/}
      {/*  <div style={{ fontSize: 15, color: '#7A72A8', marginBottom: 22 }}>*/}
      {/*    관심사가 같은 사람들과 이야기하고, 함께할 사람을 찾아보세요.*/}
      {/*  </div>*/}
      {/*  <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>*/}
      {/*    {!loggedIn && (*/}
      {/*      <Button type="primary" size="large" onClick={() => navigate('/signup')}*/}
      {/*        style={{ borderRadius: 16, fontWeight: 700, paddingInline: 28 }}>회원가입</Button>*/}
      {/*    )}*/}
      {/*    <Button size="large" onClick={() => navigate('/gen/recruit')}*/}
      {/*      style={{ borderRadius: 16, paddingInline: 24 }}>모집 둘러보기</Button>*/}
      {/*  </div>*/}
      {/*</div>*/}

      {/*
        취미 도감 — 타일마다 고정 색(hobbyColor)으로 채우고, 우측은 썸네일(없으면 이름 첫 글자).
        그리드는 gridAutoRows: 1fr — 행마다 카드 높이가 달라지면 사진 크기도 달라 보인다.
      */}
      <section>
        <SectionHead
          eyebrow="도감"
          title="어떤 취미부터 시작할까"
          right={loggedIn && myIds.size > 0
            ? <span style={{ fontSize: 13, color: gen.inkSoft, fontWeight: 600 }} className="gen-nums">담은 취미 {myIds.size} / {categories.length}</span>
            : undefined}
        />
        {categories.length === 0 ? (
          <Empty description="등록된 취미가 없습니다." />
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 280px), 1fr))', gridAutoRows: '1fr', gap: 12 }}>
            {categories.map((c, i) => {
              const mine = myIds.has(c.hobbyId)
              const hc = hobbyColor(c.hobbyId) // 취미마다 고정된 색(순번·정렬과 무관)
              const no = String(i + 1).padStart(2, '0')
              return (
                <div
                  key={c.hobbyId}
                  className={`gen-tile gen-rise${mine ? ' is-mine' : ''}`}
                  onClick={() => navigate(`/gen/hobby/${c.hobbyId}`)}
                  style={{ background: hc.gradient, animationDelay: `${Math.min(i, 9) * 40}ms` }}
                >
                  {/* 우측 아트 — 썸네일이 있으면 이미지, 없으면 이름 첫 글자 */}
                  {c.thumbId
                    ? <img aria-hidden className="gen-tile-art" src={`/api/pub/image/${c.thumbId}`} alt="" />
                    : <span aria-hidden className="gen-tile-ghost">{c.name.slice(0, 1)}</span>}
                  {loggedIn && (
                    <button
                      type="button" className="gen-tile-fav"
                      aria-label={mine ? '담기 취소' : '내 취미 담기'}
                      onClick={(e) => { e.stopPropagation(); toggleMy(c.hobbyId) }}
                    >
                      {mine ? '♥' : '♡'}
                    </button>
                  )}
                  {/* 텍스트는 좌측 60%까지만 — 우측 아트와 겹치지 않게 */}
                  <div style={{ flex: 1, minWidth: 0, position: 'relative', zIndex: 1, maxWidth: '60%' }}>
                    <div className="gen-tile-no">NO.{no}</div>
                    <div className="gen-tile-name">{c.name}</div>
                    {c.summary && <div className="gen-tile-sum" style={{ marginTop: 6 }}>{c.summary}</div>}
                    <div style={{ display: 'flex', gap: 6, marginTop: 10, flexWrap: 'wrap', alignItems: 'center' }}>
                      {c.difficultyNm && <span className="gen-tile-chip">{c.difficultyNm}</span>}
                      <span className="gen-tile-meta">멤버 {c.memberCnt ?? 0}</span>
                      <span className="gen-tile-meta" style={{ opacity: 0.5 }}>·</span>
                      <span className="gen-tile-meta">글 {c.count}</span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </section>

      {/* 지금 모집 중 — 메타는 라벨+값 쌍으로(아이콘 이모지 대신 읽히는 정보로) */}
      <section>
        <SectionHead
          eyebrow="모집"
          title="지금 함께할 사람들"
          right={<a style={{ color: TEAL, fontWeight: 600 }} onClick={() => navigate('/gen/recruit')}>전체 보기</a>}
        />
        {recruits.length === 0 ? (
          <Empty description="진행 중인 모집이 없습니다." />
        ) : (
          <Row gutter={[12, 12]}>
            {recruits.map((r) => (
              <Col key={r.rowId} xs={24} sm={12}>
                <Card hoverable onClick={() => navigate(`/gen/recruit/${r.rowId}`)} styles={{ body: { padding: 16 } }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <span className="gen-tile-chip">{r.hobbyNm}</span>
                    {statusTag(r.statusCd, r.statusNm)}
                  </div>
                  <div style={{ fontSize: 15.5, fontWeight: 700, letterSpacing: '-0.015em', marginBottom: 10 }}>{r.title}</div>
                  <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap' }}>
                    <Meta label="지역" value={[r.areaNm, r.region].filter(Boolean).join(' ') || '-'} />
                    <Meta label="일정" value={r.meetDt || '미정'} />
                    <Meta label="참여" value={`${r.acceptedCnt ?? 0}${Number(r.capacity) > 0 ? ` / ${r.capacity}` : ''}`} />
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </section>

      {/* 이번 주 베스트 — 최근 7일 참여도(좋아요·댓글·조회) 상위 */}
      {best.length > 0 && (
        <section>
          <SectionHead eyebrow="인기" title="이번 주 많이 읽은 글" />
          <Card styles={{ body: { padding: 0 } }}>
            {best.map((p, i) => (
              <div key={p.rowId} className="gen-row"
                onClick={() => navigate(`/gen/board/${p.bbsinfoId}?post=${p.rowId}`)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', cursor: 'pointer',
                  borderTop: i === 0 ? 'none' : `1px solid ${gen.line}`,
                }}>
                {/* 순위는 숫자 자체로 — 1위만 강조하고 나머지는 조용히(원형 배지 남발 방지) */}
                <span className="gen-nums" style={{
                  flexShrink: 0, width: 20, textAlign: 'right', fontSize: i === 0 ? 16 : 14,
                  fontWeight: i === 0 ? 800 : 600, color: i === 0 ? gen.primary : gen.inkFaint,
                }}>{i + 1}</span>
                <span className="gen-tile-chip" style={{ flexShrink: 0 }}>{p.bbsinfoNm}</span>
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 500 }}>{p.title}</span>
                <span className="gen-nums" style={{ fontSize: 12, color: gen.inkFaint, flexShrink: 0 }}>
                  좋아요 {Number(p.goodCnt) || 0} · 댓글 {Number(p.commentCnt) || 0}
                </span>
              </div>
            ))}
          </Card>
        </section>
      )}

      {/* 최근 이야기 */}
      <section>
        <SectionHead eyebrow="이야기" title="방금 올라온 글" />
        {posts.length === 0 ? (
          <Empty description="등록된 글이 없습니다." />
        ) : (
          <Card styles={{ body: { padding: 0 } }}>
            {posts.map((p, i) => (
              <div key={p.rowId} className="gen-row"
                onClick={() => navigate(`/gen/board/${p.bbsinfoId}?post=${p.rowId}`)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', cursor: 'pointer',
                  borderTop: i === 0 ? 'none' : `1px solid ${gen.line}`,
                }}>
                <span className="gen-tile-chip" style={{ flexShrink: 0 }}>{p.catName}</span>
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 500 }}>{p.title}</span>
                {Number(p.commentCnt) > 0 && (
                  <span className="gen-nums" style={{ color: TEAL, flexShrink: 0, fontWeight: 700, fontSize: 13 }}>{p.commentCnt}</span>
                )}
                <span className="gen-nums" style={{ fontSize: 12, color: gen.inkFaint, flexShrink: 0 }}>{p.regNm || '-'} · {p.regDt}</span>
              </div>
            ))}
          </Card>
        )}
      </section>

      {popups.map((p) => (
        <PopupLayer key={p.rowId} popup={p} onClose={() => close(p.rowId)} />
      ))}
    </div>
  )
}
