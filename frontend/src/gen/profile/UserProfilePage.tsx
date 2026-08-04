import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Button, Card, Empty, List, Rate, Result, Space, Spin, Tag } from 'antd'
import UserAvatar from '../../common/gen/components/UserAvatar'
import { getUserProfile } from '../../api/profile'
import type { UserProfile } from '../../api/profile'
import { reviewApi } from '../../api/review'
import type { Review, ReviewStats } from '../../api/review'
import { me } from '../../api/auth'
import { tokenStore } from '../../auth/token'
import { gen } from '../theme'

/** 회원 공개 프로필 — 닉네임·프로필사진 + 담은 취미 + 주최 모집 + 작성글. 닉네임 클릭 진입(/gen/user/:userId). */
export default function UserProfilePage() {
  const { userId } = useParams()
  const navigate = useNavigate()
  const [data, setData] = useState<UserProfile | null>(null)
  const [status, setStatus] = useState<'loading' | 'ok' | 'error'>('loading')
  const [myId, setMyId] = useState<string | undefined>()
  const [stats, setStats] = useState<ReviewStats | null>(null)
  const [reviews, setReviews] = useState<Review[]>([])

  useEffect(() => {
    setStatus('loading'); setData(null); setStats(null); setReviews([])
    if (!userId) { setStatus('error'); return }
    getUserProfile(userId)
      .then((d) => { setData(d); setStatus('ok') })
      .catch(() => setStatus('error'))
    // 신뢰지표(평균 별점)·받은 후기 — 실패해도 프로필 본문은 표시
    reviewApi.stats(userId).then(setStats).catch(() => {})
    reviewApi.listByTarget(userId).then(setReviews).catch(() => {})
  }, [userId])

  // 본인 프로필에는 쪽지 버튼을 숨기기 위해 로그인 사용자 확인
  useEffect(() => {
    if (!tokenStore.get()) return
    me().then((m) => setMyId(m.userId)).catch(() => {})
  }, [])

  if (status === 'loading') return <div style={{ textAlign: 'center', padding: '80px 0' }}><Spin size="large" /></div>
  if (status === 'error' || !data) return <Result status="warning" title="회원을 찾을 수 없습니다." />

  const hobbies = data.hobbies ?? []
  const recruits = data.recruits ?? []
  const posts = data.posts ?? []

  return (
    <div style={{ maxWidth: 860, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 헤더 */}
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap' }}>
          <UserAvatar fileId={data.profileFileId} name={data.nickname} size={72} showName={false} />
          <div style={{ flex: 1, minWidth: 160 }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: gen.heroText }}>{data.nickname || '회원'}</div>
            {Number(stats?.reviewCnt) > 0 && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
                <Rate disabled allowHalf value={Number(stats?.avgRating) || 0} style={{ fontSize: 14 }} />
                <span style={{ fontWeight: 700, color: gen.primary }}>{stats?.avgRating}</span>
                <span style={{ color: '#999', fontSize: 12 }}>({stats?.reviewCnt}개 후기)</span>
              </div>
            )}
            <div style={{ color: '#888', fontSize: 13, marginTop: 4 }}>
              담은 취미 {hobbies.length} · 작성글 {posts.length} · 주최 모집 {recruits.length}
            </div>
          </div>
          {myId && myId !== data.userId && (
            <Button type="primary" onClick={() => navigate(`/gen/message?with=${data.userId}`)}>쪽지 보내기</Button>
          )}
        </div>
      </Card>

      {/* 받은 후기(모임 함께한 회원들의 평가) */}
      <Card title={`받은 후기 (${reviews.length})`} size="small">
        {reviews.length === 0
          ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="아직 받은 후기가 없습니다." />
          : (
            <List
              size="small" dataSource={reviews}
              renderItem={(r) => (
                <List.Item>
                  <List.Item.Meta
                    avatar={<UserAvatar fileId={r.regProfileFileId} name={r.regNm} userId={undefined} size={32} showName={false} />}
                    title={
                      <Space size={8}>
                        <span>{r.regNm || '회원'}</span>
                        <Rate disabled value={Number(r.rating) || 0} style={{ fontSize: 12 }} />
                        <span style={{ fontSize: 12, color: '#999' }}>{r.regDt}</span>
                      </Space>
                    }
                    description={
                      <div>
                        {r.content && <div style={{ color: '#555', whiteSpace: 'pre-wrap' }}>{r.content}</div>}
                        <div style={{ fontSize: 12, color: '#aaa', marginTop: 2 }}>모임: {r.recruitTitle || '-'}</div>
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          )}
      </Card>

      {/* 담은 취미 */}
      <Card title="담은 취미" size="small">
        {hobbies.length === 0
          ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="담은 취미가 없습니다." />
          : (
            <Space wrap size={8}>
              {hobbies.map((h) => (
                <Tag
                  key={h.hobbyId} color="purple" style={{ cursor: 'pointer', padding: '4px 10px', fontSize: 14 }}
                  onClick={() => navigate(`/gen/hobby/${h.hobbyId}`)}
                >
                  {h.hobbyNm}{h.levelNm ? ` · ${h.levelNm}` : ''}
                </Tag>
              ))}
            </Space>
          )}
      </Card>

      {/* 주최한 모집 */}
      <Card title={`주최한 모집 (${recruits.length})`} size="small">
        {recruits.length === 0
          ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="주최한 모집이 없습니다." />
          : (
            <List
              size="small" dataSource={recruits}
              renderItem={(r) => (
                <List.Item style={{ cursor: 'pointer' }} onClick={() => navigate(`/gen/recruit/${r.dbKey}`)}>
                  <List.Item.Meta
                    title={r.title}
                    description={`${r.hobbyNm ?? '-'} · 인원 ${r.acceptedCnt ?? 0}${Number(r.capacity) > 0 ? ` / ${r.capacity}` : ' (제한없음)'} · ${r.regDt ?? ''}`}
                  />
                  {r.statusCd === 'RECRUIT02' ? <Tag>마감</Tag> : <Tag color="green">{r.statusNm || '모집중'}</Tag>}
                </List.Item>
              )}
            />
          )}
      </Card>

      {/* 작성글 */}
      <Card title={`작성글 (${posts.length})`} size="small">
        {posts.length === 0
          ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="작성한 글이 없습니다." />
          : (
            <List
              size="small" dataSource={posts}
              renderItem={(p) => (
                <List.Item style={{ cursor: 'pointer' }} onClick={() => navigate(`/gen/board/${p.bbsinfoId}?post=${p.dbKey}`)}>
                  <List.Item.Meta
                    title={p.title}
                    description={`${p.bbsinfoNm ?? '-'} · 💬 ${p.commentCnt ?? 0} · ❤ ${p.goodCnt ?? 0} · ${p.regDt ?? ''}`}
                  />
                </List.Item>
              )}
            />
          )}
      </Card>
    </div>
  )
}
