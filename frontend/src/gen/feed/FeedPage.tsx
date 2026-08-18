import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Card, Empty, List, Pagination, Segmented, Space, Spin, Tag, message } from 'antd'
import UserAvatar from '../../common/gen/components/UserAvatar'
import { tokenStore } from '../../auth/token'
import { feedApi } from './feed.api'
import type { FeedItem } from './feed.api'
import { gen } from '../theme'

const PAGE_SIZE = 20

/** 모임일까지 남은 일수(오늘=D-day, 지남=null). */
function dday(meetDt?: string): string | null {
  if (!meetDt) return null
  const today = new Date().toISOString().slice(0, 10)
  if (meetDt < today) return null
  const diff = Math.round((new Date(meetDt).getTime() - new Date(today).getTime()) / 86400000)
  return diff === 0 ? 'D-day' : `D-${diff}`
}

/**
 * 내 취미 피드 — 담은 취미의 새 글과 모집을 한 타임라인으로 모아본다.
 * 로그인 필요(비로그인은 로그인 유도). 담은 취미가 없으면 '나의 취미'로 안내.
 */
export default function FeedPage() {
  const navigate = useNavigate()
  const loggedIn = !!tokenStore.get()

  const [items, setItems] = useState<FeedItem[]>([])
  const [filter, setFilter] = useState<'' | 'BBS' | 'RECRUIT'>('')
  const [page, setPage] = useState(1)
  const [totalCount, setTotalCount] = useState(0)
  const [myHobbyCnt, setMyHobbyCnt] = useState(0)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    if (!loggedIn) return
    setLoading(true)
    try {
      const r = await feedApi.list(filter, page, PAGE_SIZE)
      setItems(r.list ?? [])
      setTotalCount(r.totalCount ?? 0)
      setMyHobbyCnt(r.myHobbyCnt ?? 0)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '피드 조회 실패')
    } finally {
      setLoading(false)
    }
  }, [loggedIn, filter, page])

  useEffect(() => { load() }, [load])

  if (!loggedIn) {
    return (
      <Card style={{ maxWidth: 720, margin: '0 auto', textAlign: 'center' }}>
        <div style={{ fontSize: 18, fontWeight: 700, color: gen.heroText, marginBottom: 8 }}>내 취미 피드</div>
        <div style={{ color: '#777', marginBottom: 16 }}>담은 취미의 새 글과 모집을 모아서 보여드려요. 로그인이 필요합니다.</div>
        <Button type="primary" onClick={() => navigate('/login')}>로그인</Button>
      </Card>
    )
  }

  const goItem = (it: FeedItem) => {
    if (it.feedType === 'RECRUIT') navigate(`/gen/recruit/${it.rowId}`)
    else navigate(`/gen/board/${it.bbsinfoId}?post=${it.rowId}`)
  }

  return (
    <div style={{ maxWidth: 860, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card
        title={<span style={{ color: gen.heroText }}>내 취미 피드</span>}
        extra={
          <Segmented
            size="small" value={filter}
            onChange={(v) => { setFilter(v as '' | 'BBS' | 'RECRUIT'); setPage(1) }}
            options={[
              { value: '', label: '전체' },
              { value: 'BBS', label: '글' },
              { value: 'RECRUIT', label: '모집' },
            ]}
          />
        }
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: '48px 0' }}><Spin size="large" /></div>
        ) : items.length === 0 ? (
          myHobbyCnt === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="담은 취미가 없어요. 취미를 담으면 그 취미의 새 글과 모집이 여기 모입니다."
            >
              <Button type="primary" onClick={() => navigate('/gen/onboarding')}>관심 취미 고르기</Button>
            </Empty>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="담은 취미에 아직 새 글·모집이 없어요." />
          )
        ) : (
          <>
            <List
              dataSource={items} size="small"
              renderItem={(it) => (
                <List.Item style={{ cursor: 'pointer' }} onClick={() => goItem(it)}>
                  <List.Item.Meta
                    avatar={<UserAvatar name={it.regNm || '회원'} handle={it.regHandle} size={36} showName={false} />}
                    title={
                      <Space size={8} wrap>
                        {it.feedType === 'RECRUIT'
                          ? <Tag color="purple">모집</Tag>
                          : <Tag color="blue">글</Tag>}
                        {it.hobbyNm && <Tag>{it.hobbyNm}</Tag>}
                        {it.feedSrc === 'FOLLOW' && <Tag color="magenta">팔로우</Tag>}
                        <span style={{ fontWeight: 600 }}>{it.title}</span>
                        {it.mineYn === 'Y' && <Tag color="gold">내 글</Tag>}
                      </Space>
                    }
                    description={
                      it.feedType === 'RECRUIT' ? (
                        <Space size={8} wrap style={{ fontSize: 13, color: '#888' }}>
                          <span>{it.regNm || '회원'}</span>
                          {[it.areaNm, it.region].filter(Boolean).length > 0 && (
                            <span>· {[it.areaNm, it.region].filter(Boolean).join(' ')}</span>
                          )}
                          {it.meetDt && <span>· {it.meetDt}</span>}
                          {dday(it.meetDt) && <Tag color="green">{dday(it.meetDt)}</Tag>}
                          {it.statusCd === 'RECRUIT02'
                            ? <Tag>마감</Tag>
                            : <span>· 참여 {it.acceptedCnt ?? 0}{Number(it.capacity) > 0 ? `/${it.capacity}` : ''}</span>}
                          <span>· {it.regDt}</span>
                        </Space>
                      ) : (
                        <Space size={8} wrap style={{ fontSize: 13, color: '#888' }}>
                          <span>{it.regNm || '회원'}</span>
                          <span>· 💬 {it.commentCnt ?? 0}</span>
                          <span>· ❤ {it.goodCnt ?? 0}</span>
                          <span>· {it.regDt}</span>
                        </Space>
                      )
                    }
                  />
                </List.Item>
              )}
            />
            {totalCount > PAGE_SIZE && (
              <div style={{ textAlign: 'center', marginTop: 12 }}>
                <Pagination
                  current={page} pageSize={PAGE_SIZE} total={totalCount} showSizeChanger={false}
                  onChange={setPage}
                />
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  )
}
