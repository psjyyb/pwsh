import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Empty, Space, Spin, Tag, message } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import SafeHtml from '../../common/SafeHtml'
import { hobbyApi } from '../../adm/hobby/hobby.api'
import type { Hobby } from '../../adm/hobby/hobby.api'
import { BBS_LIST_URL } from '../../adm/bbs/bbs.api'
import type { Bbs } from '../../adm/bbs/bbs.api'
import { recruitApi } from '../recruit/recruit.api'
import type { Recruit } from '../recruit/recruit.api'

const TEAL = '#00897b'

/**
 * 취미 허브(/gen/hobby/:id) — 입문자용 소개/가이드 + 그 취미의 게시판 최근 글 · 모집.
 * 취미 하나의 세 얼굴(정보·소통·모임)을 한 화면에 모은다.
 */
export default function GenHobby() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [hobby, setHobby] = useState<Hobby | null>(null)
  const [posts, setPosts] = useState<Bbs[]>([])
  const [recruits, setRecruits] = useState<Recruit[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    hobbyApi.view(id).then(async (h) => {
      setHobby(h)
      if (h.bbsinfoId) {
        const res = await apiPost<ListResult<Bbs>>(BBS_LIST_URL, { bbsinfoId: h.bbsinfoId, pageIndex: 1, size: 5 })
          .catch(() => ({ list: [], totCnt: 0 } as ListResult<Bbs>))
        setPosts(res.list.filter((b) => Number(b.bbsDepth ?? 0) === 0))
      }
      const r = await recruitApi.list({ hobbyId: id, pageIndex: 1, size: 5 }).catch(() => null)
      setRecruits(r?.list ?? [])
    }).catch((e) => message.error(e instanceof Error ? e.message : '조회 실패'))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <div style={{ textAlign: 'center', padding: 60 }}><Spin /></div>
  if (!hobby) return <Empty description="취미를 찾을 수 없습니다." />

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* 헤더 */}
      <div style={{ background: '#e6f4f1', borderRadius: 16, padding: '28px 24px', display: 'flex', gap: 20, alignItems: 'center' }}>
        {hobby.thumbId && (
          <img src={`/api/pub/image/${hobby.thumbId}`} alt={hobby.hobbyNm}
            style={{ width: 96, height: 96, objectFit: 'cover', borderRadius: 12, flexShrink: 0 }} />
        )}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 24, fontWeight: 700, color: '#004d40' }}>{hobby.hobbyNm}</span>
            {hobby.difficultyNm && <Tag color="cyan">{hobby.difficultyNm}</Tag>}
          </div>
          {hobby.summary && <div style={{ fontSize: 15, color: '#00695c', marginTop: 6 }}>{hobby.summary}</div>}
          <Space style={{ marginTop: 14 }}>
            {hobby.bbsinfoId && <Button type="primary" onClick={() => navigate(`/gen/board/${hobby.bbsinfoId}`)}>게시판 가기</Button>}
            <Button onClick={() => navigate('/gen/recruit')}>모집 보기</Button>
          </Space>
        </div>
      </div>

      {/* 소개 */}
      {hobby.intro && (
        <section>
          <h3 style={{ fontSize: 18, fontWeight: 600, marginBottom: 10 }}>소개</h3>
          <Card><SafeHtml html={hobby.intro} /></Card>
        </section>
      )}

      {/* 입문 가이드 + 장비/비용 */}
      <section>
        <h3 style={{ fontSize: 18, fontWeight: 600, marginBottom: 10 }}>입문 가이드</h3>
        <Card>
          {hobby.guide ? <SafeHtml html={hobby.guide} /> : <span style={{ color: '#999' }}>준비 중입니다.</span>}
          {(hobby.equipment || hobby.estCost) && (
            <Descriptions bordered column={1} size="small" style={{ marginTop: 16 }}>
              {hobby.equipment && <Descriptions.Item label="필요 장비">{hobby.equipment}</Descriptions.Item>}
              {hobby.estCost && <Descriptions.Item label="대략 비용">{hobby.estCost}</Descriptions.Item>}
            </Descriptions>
          )}
        </Card>
      </section>

      {/* 지금 모집 중 */}
      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
          <h3 style={{ fontSize: 18, fontWeight: 600, margin: 0 }}>이 취미 모집</h3>
          <a style={{ color: TEAL }} onClick={() => navigate('/gen/recruit')}>전체 보기</a>
        </div>
        {recruits.length === 0 ? (
          <Empty description="진행 중인 모집이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Card styles={{ body: { padding: 0 } }}>
            {recruits.map((r, i) => (
              <div key={r.dbKey} onClick={() => navigate('/gen/recruit')}
                style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', cursor: 'pointer', borderTop: i === 0 ? 'none' : '1px solid #f0f0f0' }}>
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
          <h3 style={{ fontSize: 18, fontWeight: 600, margin: 0 }}>최근 글</h3>
          {hobby.bbsinfoId && <a style={{ color: TEAL }} onClick={() => navigate(`/gen/board/${hobby.bbsinfoId}`)}>게시판 가기</a>}
        </div>
        {posts.length === 0 ? (
          <Empty description="등록된 글이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Card styles={{ body: { padding: 0 } }}>
            {posts.map((p, i) => (
              <div key={p.dbKey} onClick={() => navigate(`/gen/board/${hobby.bbsinfoId}`)}
                style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', cursor: 'pointer', borderTop: i === 0 ? 'none' : '1px solid #f0f0f0' }}>
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.title}</span>
                {Number(p.commentCnt) > 0 && <span style={{ color: TEAL }}>[{p.commentCnt}]</span>}
                <span style={{ fontSize: 12, color: '#aaa' }}>{p.regNm || p.regId} · {p.regDt}</span>
              </div>
            ))}
          </Card>
        )}
      </section>
    </div>
  )
}
