import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { Badge, Button, Card, Empty, Input, List, Popconfirm, Spin, message as toast } from 'antd'
import MemberAvatar from '../../common/gen/components/MemberAvatar'
import { messageApi } from '../../api/message'
import type { Conversation, Message } from '../../api/message'
import { recruitApi } from '../recruit/recruit.api'
import { useEventStream } from '../../common/gen/useEventStream'
import { gen } from '../theme'

/** SSE가 끊겼을 때만 쓰는 폴백 폴링 주기(ms). 푸시가 살아있으면 폴링하지 않는다. */
const POLL_MS = 5000

/** 쪽지 본문의 내부 경로(/gen/...)만 링크로 만든다. */
const INNER_PATH = /(\/gen\/[A-Za-z0-9/_\-?=&.]+)/g

/**
 * 본문에서 내부 경로만 클릭 가능하게 렌더.
 * 외부 URL(http…)은 일부러 링크로 만들지 않는다 — 쪽지는 아무나 보낼 수 있어서
 * 자동 링크가 되면 피싱 통로가 된다. 내부 경로만 앱 라우팅으로 이동시킨다.
 */
function linkify(text: string, mine: boolean, go: (path: string) => void) {
  const parts = text.split(INNER_PATH)
  return parts.map((p, i) =>
    INNER_PATH.test(p) && p.startsWith('/gen/') ? (
      <a
        key={i}
        onClick={(e) => { e.stopPropagation(); go(p) }}
        style={{ color: mine ? '#fff' : gen.primary, textDecoration: 'underline', cursor: 'pointer' }}
      >
        {p}
      </a>
    ) : (
      <span key={i}>{p}</span>
    ),
  )
}

/**
 * 쪽지(1:1 메시지) — 좌: 대화 목록, 우: 선택한 상대와의 대화 + 입력.
 * /gen/message?with={handle} 로 특정 상대와 바로 대화 시작(프로필 '쪽지 보내기', 모집 '문의하기'에서 진입).
 * ?ref=recruit:{id} 가 함께 오면 어떤 모집에 대한 문의인지 입력창에 미리 채운다.
 */
export default function MessagePage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const withId = params.get('with') ?? undefined
  const ref = params.get('ref') ?? undefined
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

  const loadThread = useCallback(async (otherHandle: string) => {
    setThreadLoading(true)
    try { setThread(await messageApi.thread(otherHandle)) }
    catch (e) { toast.error(e instanceof Error ? e.message : '대화 조회 실패') }
    finally { setThreadLoading(false) }
  }, [])

  useEffect(() => { loadConvs() }, [loadConvs])

  useEffect(() => {
    if (withId) loadThread(withId).then(() => loadConvs()) // 읽음처리 반영
    else setThread([])
  }, [withId, loadThread, loadConvs])

  /** 대화·목록을 조용히(스피너 없이) 다시 읽는다 — 푸시 수신 시/폴백 주기에 사용. */
  const refreshQuiet = useCallback(async () => {
    if (!withId) return
    try {
      const [t, c] = await Promise.all([messageApi.thread(withId), messageApi.convList()])
      setThread(t)
      setConvs(c)
    } catch { /* 일시적 실패는 다음 신호에 회복 */ }
  }, [withId])

  // 서버 푸시(SSE): 새 쪽지가 오면 즉시 갱신. 연결 상태에 따라 아래 폴백이 켜진다.
  const streamed = useEventStream('/api/adm/message/selectMessageListStream.do', (ev) => {
    if (ev === 'message') refreshQuiet()
  })

  /*
    폴백 폴링 — SSE가 끊겼을 때만 동작한다(연결 중이면 요청을 보내지 않는다).
    푸시가 정상일 때 서버 부하를 0으로 두면서도, 프록시·네트워크 문제로 스트림이 막히는 환경에서
    기능이 멈추지 않게 한다. 탭이 백그라운드면 건너뛴다.
  */
  useEffect(() => {
    if (!withId || streamed) return
    const tick = () => {
      if (document.visibilityState !== 'visible') return
      refreshQuiet()
    }
    const id = window.setInterval(tick, POLL_MS)
    return () => window.clearInterval(id)
  }, [withId, streamed, refreshQuiet])

  // 모집 문의로 들어온 경우 입력창을 미리 채운다(사용자가 이미 입력 중이면 건드리지 않음)
  const prefilled = useRef(false)
  useEffect(() => {
    const id = ref?.startsWith('recruit:') ? ref.slice('recruit:'.length) : ''
    if (!id || prefilled.current) return
    prefilled.current = true
    recruitApi.view(id)
      .then((r) => {
        const head = `[모집 문의] ${r.title ?? ''}`
        const when = r.meetDt ? `\n일정: ${r.meetDt}` : ''
        const where = [r.areaName, r.region].filter(Boolean).join(' ')
        // 경로를 함께 넣어 대화에서 바로 눌러 이동할 수 있게 한다(아래 linkify가 링크로 렌더)
        const link = `\n/gen/recruit/${id}`
        setText((prev) => (prev ? prev : `${head}${when}${where ? `\n지역: ${where}` : ''}${link}\n\n`))
      })
      .catch(() => { /* 모집을 못 읽어도 대화는 그대로 쓸 수 있게 둔다 */ })
  }, [ref])

  // 새 메시지/대화 전환 시 최신 메시지로 스크롤(대화창 내부만)
  useEffect(() => { bottomRef.current?.scrollIntoView({ block: 'nearest' }) }, [thread])

  const openConv = (otherHandle?: string) => { if (otherHandle) setParams({ with: otherHandle }) }

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

  const removeConv = async (otherHandle: string) => {
    try {
      await messageApi.removeConv(otherHandle)
      toast.success('대화를 삭제했습니다.')
      if (withId === otherHandle) setParams({})
      loadConvs()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '삭제 실패')
    }
  }

  const current = convs.find((c) => c.otherHandle === withId)
  const otherName = current?.otherName || withId

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
                onClick={() => openConv(c.otherHandle)}
                style={{ cursor: 'pointer', background: c.otherHandle === withId ? gen.heroTint : undefined, borderRadius: 8, paddingInline: 8 }}
                actions={[
                  <Popconfirm key="del" title="이 대화를 삭제하시겠습니까?" onConfirm={(e) => { e?.stopPropagation(); removeConv(c.otherHandle!) }} okText="삭제" okButtonProps={{ danger: true }} cancelText="취소">
                    <a onClick={(e) => e.stopPropagation()} style={{ fontSize: 12, color: '#999' }}>삭제</a>
                  </Popconfirm>,
                ]}
              >
                <List.Item.Meta
                  avatar={<Badge count={Number(c.unreadCnt) || 0} size="small"><MemberAvatar fileId={c.otherFileId} name={c.otherName} size={34} showName={false} /></Badge>}
                  title={<span style={{ fontWeight: Number(c.unreadCnt) > 0 ? 700 : 500 }}>{c.otherName || '-'}</span>}
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
        title={withId ? <span style={{ cursor: 'pointer' }} onClick={() => navigate(`/gen/member/${withId}`)}>{otherName} <span style={{ fontSize: 12, color: '#999' }}>프로필</span></span> : '대화'}
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
                    <div key={m.rowId} style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start' }}>
                      <div style={{ maxWidth: '76%' }}>
                        <div style={{
                          background: mine ? gen.primary : '#F2F0FA', color: mine ? '#fff' : '#333',
                          padding: '8px 12px', borderRadius: 14, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                        }}>
                          {linkify(m.content ?? '', mine, (p) => navigate(p))}
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
