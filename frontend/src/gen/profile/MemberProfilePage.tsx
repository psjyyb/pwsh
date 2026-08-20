import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Button, Card, Empty, List, Popconfirm, Rate, Result, Space, Spin, Tag, Tooltip, message } from 'antd'
import { badgesOf, num } from '../badges'
import MemberAvatar from '../../common/gen/components/MemberAvatar'
import { getMemberProfile } from '../../api/profile'
import type { MemberProfile } from '../../api/profile'
import { reviewApi } from '../../api/review'
import type { Review, ReviewStats } from '../../api/review'
import { blockApi } from '../../api/block'
import { followApi } from '../../api/follow'
import { me } from '../../api/auth'
import { tokenStore } from '../../auth/token'
import { gen } from '../theme'

/** 회원 공개 프로필 — 닉네임·프로필사진 + 담은 취미 + 주최 모집 + 작성글. 닉네임 클릭 진입(/gen/member/:memberId). */
export default function MemberProfilePage() {
  const { memberId: handle } = useParams() // 라우트 파라미터는 공개 식별자(handle)
  const navigate = useNavigate()
  const [data, setData] = useState<MemberProfile | null>(null)
  const [status, setStatus] = useState<'loading' | 'ok' | 'error'>('loading')
  const [myId, setMyId] = useState<string | undefined>()
  const [stats, setStats] = useState<ReviewStats | null>(null)
  const [reviews, setReviews] = useState<Review[]>([])

  useEffect(() => {
    setStatus('loading'); setData(null); setStats(null); setReviews([])
    if (!handle) { setStatus('error'); return }
    getMemberProfile(handle)
      .then((d) => { setData(d); setStatus('ok') })
      .catch(() => setStatus('error'))
    // 신뢰지표(평균 별점)·받은 후기 — 실패해도 프로필 본문은 표시
    reviewApi.stats(handle).then(setStats).catch(() => {})
    reviewApi.listByTarget(handle).then(setReviews).catch(() => {})
  }, [handle])

  const [blocked, setBlocked] = useState(false)
  const [following, setFollowing] = useState(false)
  const [followCnt, setFollowCnt] = useState<{ follower: number; followingCnt: number }>({ follower: 0, followingCnt: 0 })

  // 본인 프로필에는 쪽지/차단 버튼을 숨기기 위해 로그인 사용자 확인
  useEffect(() => {
    if (!tokenStore.get()) return
    me().then((m) => setMyId(m.handle)).catch(() => {}) // 내 handle로 본인 판정
  }, [])

  // 차단·팔로우 여부(로그인 + 타인 프로필일 때만)
  useEffect(() => {
    if (!handle || !myId || myId === handle) { setBlocked(false); setFollowing(false); return }
    blockApi.check(handle).then((v) => setBlocked(v === 'Y')).catch(() => {})
    followApi.check(handle).then((v) => setFollowing(v === 'Y')).catch(() => {})
  }, [handle, myId])

  // 팔로워·팔로잉 수는 비로그인도 볼 수 있다(공개 지표)
  useEffect(() => {
    if (!handle) return
    followApi.counts(handle)
      .then((c) => setFollowCnt({ follower: Number(c.followerCnt) || 0, followingCnt: Number(c.followingCnt) || 0 }))
      .catch(() => {})
  }, [handle])

  const toggleFollow = async () => {
    if (!handle) return
    try {
      const r = await followApi.toggle(handle)
      const on = r.followedYn === 'Y'
      setFollowing(on)
      setFollowCnt((prev) => ({ ...prev, follower: Math.max(0, prev.follower + (on ? 1 : -1)) }))
      message.success(on ? '팔로우했습니다. 새 모집이 열리면 알려드릴게요.' : '팔로우를 해제했습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '팔로우 처리 실패')
    }
  }

  const toggleBlock = async () => {
    if (!handle) return
    try {
      const r = await blockApi.toggle(handle)
      const on = r.blockedYn === 'Y'
      setBlocked(on)
      message.success(on ? '차단했습니다. 이 회원은 나에게 쪽지를 보낼 수 없습니다.' : '차단을 해제했습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '차단 처리 실패')
    }
  }

  if (status === 'loading') return <div style={{ textAlign: 'center', padding: '80px 0' }}><Spin size="large" /></div>
  if (status === 'error' || !data) return <Result status="warning" title="회원을 찾을 수 없습니다." />

  const hobbies = data.hobbies ?? []
  const recruits = data.recruits ?? []
  const posts = data.posts ?? []
  const badges = badgesOf({
    attended: num(data.attend?.attendedCnt),
    noshow: num(data.attend?.noshowCnt),
    hosted: recruits.length,
    reviewCnt: num(stats?.reviewCnt),
    avgRating: num(stats?.avgRating),
  })

  return (
    <div style={{ maxWidth: 860, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 헤더 */}
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap' }}>
          <MemberAvatar fileId={data.profileFileId} name={data.nickname} size={72} showName={false} />
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
            <div style={{ color: '#888', fontSize: 13, marginTop: 2 }}>
              팔로워 <b style={{ color: gen.primary }}>{followCnt.follower}</b> · 팔로잉 {followCnt.followingCnt}
            </div>
            {/* 참석 신뢰지표 — 주최자가 기록한 모임만 집계. 노쇼가 있으면 눈에 띄게 */}
            {(Number(data.attend?.attendedCnt) + Number(data.attend?.absentCnt) + Number(data.attend?.noshowCnt)) > 0 && (
              <Space size={6} style={{ marginTop: 6 }} wrap>
                <Tag color="green">참석 {Number(data.attend?.attendedCnt) || 0}</Tag>
                {Number(data.attend?.absentCnt) > 0 && <Tag>불참 {data.attend?.absentCnt}</Tag>}
                {Number(data.attend?.noshowCnt) > 0 && <Tag color="red">노쇼 {data.attend?.noshowCnt}</Tag>}
              </Space>
            )}
            {/* 활동 배지 — 위 숫자에서 파생. 기준은 툴팁으로 밝힌다(불투명한 배지는 장식일 뿐) */}
            {badges.length > 0 && (
              <Space size={6} style={{ marginTop: 8 }} wrap>
                {badges.map((b) => (
                  <Tooltip key={b.key} title={b.desc}>
                    <Tag color={b.color} style={{ fontWeight: 600, cursor: 'default' }}>{b.label}</Tag>
                  </Tooltip>
                ))}
              </Space>
            )}
          </div>
          {myId && myId !== data.handle && (
            <Space>
              {/* 팔로우하면 이 회원이 새 모집을 열 때 알림이 오고, 내 피드에 이 회원의 글·모집이 함께 뜬다 */}
              <Button type={following ? 'default' : 'primary'} ghost={following} onClick={toggleFollow}>
                {following ? '✓ 팔로잉' : '+ 팔로우'}
              </Button>
              <Button onClick={() => navigate(`/gen/message?with=${data.handle}`)}>쪽지 보내기</Button>
              {blocked ? (
                <Popconfirm title="차단 해제" description="이 회원의 쪽지를 다시 받습니다." onConfirm={toggleBlock} okText="해제" cancelText="취소">
                  <Button>차단 해제</Button>
                </Popconfirm>
              ) : (
                <Popconfirm title="회원 차단" description="이 회원은 나에게 쪽지를 보낼 수 없게 됩니다." onConfirm={toggleBlock} okText="차단" okButtonProps={{ danger: true }} cancelText="취소">
                  <Button danger>차단</Button>
                </Popconfirm>
              )}
            </Space>
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
                    avatar={<MemberAvatar fileId={r.regProfileFileId} name={r.regName} handle={r.regHandle} size={32} showName={false} />}
                    title={
                      <Space size={8}>
                        <span>{r.regName || '회원'}</span>
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
                  {h.hobbyName}{h.levelName ? ` · ${h.levelName}` : ''}
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
                <List.Item style={{ cursor: 'pointer' }} onClick={() => navigate(`/gen/recruit/${r.rowId}`)}>
                  <List.Item.Meta
                    title={r.title}
                    description={`${r.hobbyName ?? '-'} · 인원 ${r.acceptedCnt ?? 0}${Number(r.capacity) > 0 ? ` / ${r.capacity}` : ' (제한없음)'} · ${r.regDt ?? ''}`}
                  />
                  {r.statusCd === 'RECRUIT02' ? <Tag>마감</Tag> : <Tag color="green">{r.statusName || '모집중'}</Tag>}
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
                <List.Item style={{ cursor: 'pointer' }} onClick={() => navigate(`/gen/board/${p.boardId}?post=${p.rowId}`)}>
                  <List.Item.Meta
                    title={p.title}
                    description={`${p.boardName ?? '-'} · 💬 ${p.commentCnt ?? 0} · ❤ ${p.goodCnt ?? 0} · ${p.regDt ?? ''}`}
                  />
                </List.Item>
              )}
            />
          )}
      </Card>
    </div>
  )
}
