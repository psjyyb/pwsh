import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Empty, Space, Spin, Tag, message } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import SafeHtml from '../../common/SafeHtml'
import CodeSelect from '../../common/adm/components/CodeSelect'
import { tokenStore } from '../../auth/token'
import { hobbyApi, memberHobbyApi } from '../../adm/hobby/hobby.api'
import type { Hobby } from '../../adm/hobby/hobby.api'
import { POST_LIST_URL } from '../../adm/post/post.api'
import type { Post } from '../../adm/post/post.api'
import { recruitApi } from '../recruit/recruit.api'
import type { Recruit } from '../recruit/recruit.api'
import { gen } from '../theme'
import { PageBody, PageHead } from '../../common/gen/components/PageShell'

const TEAL = gen.primary

/**
 * 취미 허브(/gen/hobby/:id) — 입문자용 소개/가이드 + 그 취미의 게시판 최근 글 · 모집.
 * 취미 하나의 세 얼굴(정보·소통·모임)을 한 화면에 모은다.
 */
export default function GenHobby() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [hobby, setHobby] = useState<Hobby | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [recruits, setRecruits] = useState<Recruit[]>([])
  const [myLevel, setMyLevel] = useState<string | undefined>()
  const [registered, setRegistered] = useState(false)
  const [loading, setLoading] = useState(true)
  const loggedIn = !!tokenStore.get()

  useEffect(() => {
    if (!id) return
    setLoading(true)
    hobbyApi.view(id).then(async (h) => {
      setHobby(h)
      if (h.boardId) {
        const res = await apiPost<ListResult<Post>>(POST_LIST_URL, { boardId: h.boardId, pageNo: 1, pageSize: 5 })
          .catch(() => ({ list: [], totalCount: 0 } as ListResult<Post>))
        setPosts(res.list.filter((b) => Number(b.depth ?? 0) === 0))
      }
      const r = await recruitApi.list({ hobbyId: id, pageNo: 1, pageSize: 5 }).catch(() => null)
      setRecruits(r?.list ?? [])
      if (loggedIn) {
        const mine = await memberHobbyApi.list().catch(() => [])
        const m = mine.find((u) => u.hobbyId === id)
        setRegistered(!!m)
        setMyLevel(m?.levelCd)
      }
    }).catch((e) => message.error(e instanceof Error ? e.message : '조회 실패'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const saveLevel = async (v?: string) => {
    if (!id) return
    try {
      await memberHobbyApi.save(id, v) // v 없으면 담기는 유지하고 레벨만 해제
      setMyLevel(v)
      setRegistered(true)
      message.success(v ? '내 레벨을 저장했습니다.' : '레벨을 해제했습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '레벨 저장 실패')
    }
  }

  const toggleRegister = async () => {
    if (!id) return
    try {
      if (registered) {
        await memberHobbyApi.remove(id)
        setRegistered(false)
        setMyLevel(undefined)
        message.success('담기를 취소했습니다.')
      } else {
        await memberHobbyApi.save(id) // 관심 담기(레벨 없이)
        setRegistered(true)
        message.success('내 취미에 담았습니다.')
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '처리 실패')
    }
  }

  if (loading) return <PageBody><div style={{ textAlign: 'center', padding: 60 }}><Spin /></div></PageBody>
  if (!hobby) return <PageBody><Empty description="취미를 찾을 수 없습니다." /></PageBody>

  return (
    <>
      {/* 헤드 밴드(공통 셸) — 썸네일은 media, 게시판·모집 이동은 right */}
      <PageHead
        eyebrow="도감"
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            {hobby.hobbyName}
            {hobby.difficultyName && <Tag color="purple">{hobby.difficultyName}</Tag>}
          </span>
        }
        lead={hobby.summary}
        media={hobby.thumbId
          ? <img src={`/api/pub/image/${hobby.thumbId}`} alt={hobby.hobbyName} />
          : (hobby.hobbyName ?? '').slice(0, 1)}
        right={
          <Space wrap>
            {hobby.boardId && (
              <Button type="primary" onClick={() => navigate(`/gen/board/${hobby.boardId}`)}>게시판 가기</Button>
            )}
            <Button onClick={() => navigate(`/gen/recruit?hobby=${id}`)}>모집 보기</Button>
          </Space>
        }
      >
        {loggedIn && (
          <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <Button type={registered ? 'default' : 'primary'} ghost={registered} onClick={toggleRegister}>
              {registered ? '♥ 담은 취미' : '+ 내 취미 담기'}
            </Button>
            <span style={{ color: 'var(--gen-ink-soft)', fontSize: 13 }}>내 레벨</span>
            <CodeSelect pCodeId="HOBBYLV00" placeholder="선택" allowClear size="small"
              style={{ width: 140 }} value={myLevel} onChange={(v?: string) => saveLevel(v)} />
          </div>
        )}
      </PageHead>

      <PageBody>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* 소개 */}
      {hobby.intro && (
        <section>
          <h3 className="gen-h2" style={{ marginBottom: 10 }}>소개</h3>
          <Card><SafeHtml html={hobby.intro} /></Card>
        </section>
      )}

      {/* 입문 가이드 + 장비/비용 */}
      <section>
        <h3 className="gen-h2" style={{ marginBottom: 10 }}>입문 가이드</h3>
        <Card>
          {hobby.guide ? <SafeHtml html={hobby.guide} /> : <span style={{ color: '#999' }}>준비 중입니다.</span>}
          {(hobby.equipment || hobby.estimatedCost) && (
            <Descriptions bordered column={1} size="small" style={{ marginTop: 16 }}>
              {hobby.equipment && <Descriptions.Item label="필요 장비">{hobby.equipment}</Descriptions.Item>}
              {hobby.estimatedCost && <Descriptions.Item label="대략 비용">{hobby.estimatedCost}</Descriptions.Item>}
            </Descriptions>
          )}
        </Card>
      </section>

      {/* 지금 모집 중 */}
      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
          <h3 className="gen-h2">이 취미 모집</h3>
          <a style={{ color: TEAL }} onClick={() => navigate(`/gen/recruit?hobby=${id}`)}>전체 보기</a>
        </div>
        {recruits.length === 0 ? (
          <Empty description="진행 중인 모집이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Card styles={{ body: { padding: 0 } }}>
            {recruits.map((r, i) => (
              <div key={r.rowId} onClick={() => navigate(`/gen/recruit/${r.rowId}`)}
                style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', cursor: 'pointer', borderTop: i === 0 ? 'none' : `1px solid ${gen.line}` }}>
                <span style={{ flex: 1 }}>{r.title}</span>
                <span style={{ fontSize: 13, color: '#888' }}>{r.region || '-'} · {r.meetDt || '-'}</span>
                {r.statusCd === 'RECRUIT01' ? <Tag color="green">모집중</Tag> : <Tag>마감</Tag>}
              </div>
            ))}
          </Card>
        )}
      </section>

      {/* 최근 글 */}
      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
          <h3 className="gen-h2">최근 글</h3>
          {hobby.boardId && <a style={{ color: TEAL }} onClick={() => navigate(`/gen/board/${hobby.boardId}`)}>게시판 가기</a>}
        </div>
        {posts.length === 0 ? (
          <Empty description="등록된 글이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Card styles={{ body: { padding: 0 } }}>
            {posts.map((p, i) => (
              <div key={p.rowId} onClick={() => navigate(`/gen/board/${hobby.boardId}?post=${p.rowId}`)}
                style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', cursor: 'pointer', borderTop: i === 0 ? 'none' : `1px solid ${gen.line}` }}>
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.title}</span>
                {Number(p.commentCnt) > 0 && <span style={{ color: TEAL }}>[{p.commentCnt}]</span>}
                <span style={{ fontSize: 12, color: '#aaa' }}>{p.regName || '-'} · {p.regDt}</span>
              </div>
            ))}
          </Card>
        )}
      </section>
      </div>
      </PageBody>
    </>
  )
}
