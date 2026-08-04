import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { Badge, Button, Card, Empty, Input, List, Popconfirm, Spin, message as toast } from 'antd'
import UserAvatar from '../../common/gen/components/UserAvatar'
import { messageApi } from '../../api/message'
import type { Conversation, Message } from '../../api/message'
import { gen } from '../theme'

/**
 * 쪽지(1:1 메시지) — 좌: 대화 목록, 우: 선택한 상대와의 대화 + 입력.
 * /gen/message?with={userId} 로 특정 상대와 바로 대화 시작(프로필 '쪽지 보내기'에서 진입).
 */
export default function MessagePage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const withId = params.get('with') ?? undefined
  const [convs, setConvs] = useState<Conversation[]>([])
  const [convLoading, setConvLoading] = useState(false)
  const [thread, setThread] = useState<Message[]>([])
  const [threadLoading, setThreadLoading] = useState(false)
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  const loadConvs = useCallback(async () => {
    setConvLoading(true)
    try { setConvs(await messageApi.convList()) }
    catch (e) { toast.error(e instanceof Error ? e.message : '대화 목록 조회 실패') }
    finally { setConvLoading(false) }
  }, [])

  const loadThread = useCallback(async (otherId: string) => {
    setThreadLoading(true)
    try { setThread(await messageApi.thread(otherId)) }
    catch (e) { toast.error(e instanceof Error ? e.message : '대화 조회 실패') }
    finally { setThreadLoading(false) }
  }, [])

  useEffect(() => { loadConvs() }, [loadConvs])

  useEffect(() => {
    if (withId) loadThread(withId).then(() => loadConvs()) // 읽음처리 반영
    else setThread([])
  }, [withId, loadThread, loadConvs])

  // 새 메시지/대화 전환 시 최신 메시지로 스크롤(대화창 내부만)
  useEffect(() => { bottomRef.current?.scrollIntoView({ block: 'nearest' }) }, [thread])

  const openConv = (otherId?: string) => { if (otherId) setParams({ with: otherId }) }

  const send = async () => {
    const body = text.trim()
    if (!withId || !body) return
    setSending(true)
    try {
      await messageApi.send(withId, body)
      setText('')
      await loadThread(withId)
      loadConvs()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '전송 실패')
    } finally {
      setSending(false)
    }
  }

  const removeConv = async (otherId: string) => {
    try {
      await messageApi.removeConv(otherId)
      toast.success('대화를 삭제했습니다.')
      if (withId === otherId) setParams({})
      loadConvs()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '삭제 실패')
    }
  }

  const current = convs.find((c) => c.otherId === withId)
  const otherNm = current?.otherNm || withId

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
      {/* 대화 목록 */}
      <Card title="쪽지" size="small" style={{ flex: '1 1 300px', minWidth: 280 }}>
        {convLoading && convs.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
        ) : convs.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="대화가 없습니다." />
        ) : (
          <List
            size="small" dataSource={convs}
            renderItem={(c) => (
              <List.Item
                onClick={() => openConv(c.otherId)}
                style={{ cursor: 'pointer', background: c.otherId === withId ? gen.heroTint : undefined, borderRadius: 8, paddingInline: 8 }}
                actions={[
                  <Popconfirm key="del" title="이 대화를 삭제하시겠습니까?" onConfirm={(e) => { e?.stopPropagation(); removeConv(c.otherId!) }} okText="삭제" okButtonProps={{ danger: true }} cancelText="취소">
                    <a onClick={(e) => e.stopPropagation()} style={{ fontSize: 12, color: '#999' }}>삭제</a>
                  </Popconfirm>,
                ]}
              >
                <List.Item.Meta
                  avatar={<Badge count={Number(c.unreadCnt) || 0} size="small"><UserAvatar fileId={c.otherFileId} name={c.otherNm} size={34} showName={false} /></Badge>}
                  title={<span style={{ fontWeight: Number(c.unreadCnt) > 0 ? 700 : 500 }}>{c.otherNm || c.otherId}</span>}
                  description={
                    <span style={{ fontSize: 12, color: '#888' }}>
                      {c.lastMine === 'Y' ? '나: ' : ''}{(c.lastContent ?? '').slice(0, 24)} · {c.lastDt}
                    </span>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Card>

      {/* 대화창 */}
      <Card
        size="small"
        style={{ flex: '2 1 420px', minWidth: 300 }}
        title={withId ? <span style={{ cursor: 'pointer' }} onClick={() => navigate(`/gen/user/${withId}`)}>{otherNm} <span style={{ fontSize: 12, color: '#999' }}>프로필</span></span> : '대화'}
      >
        {!withId ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="왼쪽에서 대화를 선택하세요." />
        ) : (
          <>
            <div style={{ maxHeight: 420, overflowY: 'auto', padding: '4px 2px', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {threadLoading && thread.length === 0 ? (
                <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
              ) : thread.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="첫 쪽지를 보내보세요." />
              ) : (
                thread.map((m) => {
                  const mine = m.mine === 'Y'
                  return (
                    <div key={m.dbKey} style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start' }}>
                      <div style={{ maxWidth: '76%' }}>
                        <div style={{
                          background: mine ? gen.primary : '#F2F0FA', color: mine ? '#fff' : '#333',
                          padding: '8px 12px', borderRadius: 14, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                        }}>
                          {m.content}
                        </div>
                        <div style={{ fontSize: 11, color: '#aaa', marginTop: 3, textAlign: mine ? 'right' : 'left' }}>{m.regDt}</div>
                      </div>
                    </div>
                  )
                })
              )}
              <div ref={bottomRef} />
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
              <Input.TextArea
                value={text} onChange={(e) => setText(e.target.value)}
                placeholder="쪽지 내용을 입력하세요. (Enter 전송, Shift+Enter 줄바꿈)"
                autoSize={{ minRows: 2, maxRows: 5 }} maxLength={2000}
                onPressEnter={(e) => { if (!e.shiftKey) { e.preventDefault(); send() } }}
              />
              <Button type="primary" onClick={send} loading={sending} disabled={!text.trim()}>보내기</Button>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}
