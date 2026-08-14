import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Empty, Input, Popconfirm, Space, Tag, message } from 'antd'
import UserAvatar from '../../common/gen/components/UserAvatar'
import { useEventStream } from '../../common/gen/useEventStream'
import { recruitChatApi } from './recruit.api'
import type { RecruitChat } from './recruit.api'
import { gen } from '../theme'

/** SSE가 끊겼을 때만 쓰는 폴링 주기(ms). 연결되어 있으면 폴링하지 않는다. */
const POLL_MS = 5000

/**
 * 모임 단체 대화 — 주최자 + 수락된 참여자만 보이는 패널.
 * 자격 판정은 서버(목록 API 403)가 하며, 이 컴포넌트는 자격이 있다고 판단된 경우에만 렌더된다.
 * 실시간은 쪽지와 같은 SSE 허브를 쓰고(이벤트 'recruitchat'), 끊기면 폴링으로 대체한다.
 */
export default function RecruitChatPanel({ recruitId }: { recruitId: string }) {
  const [rows, setRows] = useState<RecruitChat[]>([])
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [denied, setDenied] = useState(false)
  const boxRef = useRef<HTMLDivElement>(null)
  const lastKeyRef = useRef<string | undefined>(undefined)

  /** 조용한 갱신(에러 토스트 없음) — 폴링·푸시로 반복 호출되므로 실패를 시끄럽게 알리지 않는다. */
  const load = useCallback(async () => {
    try {
      const list = await recruitChatApi.list(recruitId)
      setRows(list)
      setDenied(false)
    } catch {
      setDenied(true) // 수락 취소 등으로 자격을 잃은 경우
    }
  }, [recruitId])

  useEffect(() => { load() }, [load])

  // 새 말이 붙으면 맨 아래로 — 사용자가 위를 읽고 있을 때 끌어내리지 않도록 마지막 키가 바뀔 때만
  useEffect(() => {
    const lastKey = rows.length ? rows[rows.length - 1].dbKey : undefined
    if (lastKey !== lastKeyRef.current) {
      lastKeyRef.current = lastKey
      const box = boxRef.current
      if (box) box.scrollTop = box.scrollHeight
    }
  }, [rows])

  const streamed = useEventStream('/api/adm/recruitChat/selectRecruitChatListStream.do', (ev) => {
    if (ev === 'recruitchat') load()
  })

  useEffect(() => {
    if (streamed || denied) return // 푸시가 살아있으면 폴링 불필요
    const t = window.setInterval(load, POLL_MS)
    return () => window.clearInterval(t)
  }, [streamed, denied, load])

  const send = async () => {
    const content = text.trim()
    if (!content) return
    setSending(true)
    try {
      await recruitChatApi.send(recruitId, content)
      setText('')
      await load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '전송 실패')
    } finally {
      setSending(false)
    }
  }

  const remove = async (dbKey: string) => {
    try {
      await recruitChatApi.remove(dbKey)
      await load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '삭제 실패')
    }
  }

  if (denied) {
    return (
      <div style={{ marginTop: 20, borderTop: `1px solid ${gen.line}`, paddingTop: 12, color: gen.inkFaint }}>
        참여가 확정된 회원만 대화할 수 있습니다.
      </div>
    )
  }

  return (
    <div style={{ marginTop: 20, borderTop: `1px solid ${gen.line}`, paddingTop: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <b>모임 대화</b>
        <span style={{ fontSize: 12, color: gen.inkFaint }}>주최자와 참여 확정 멤버만 볼 수 있어요</span>
        {!streamed && <Tag color="default">지연 갱신</Tag>}
      </div>

      <div
        ref={boxRef}
        style={{
          maxHeight: 340, overflowY: 'auto', background: gen.surfaceAlt,
          borderRadius: 10, padding: rows.length ? 12 : 0,
        }}
      >
        {rows.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="첫 인사를 남겨보세요." />
        ) : (
          <Space direction="vertical" size={10} style={{ width: '100%' }}>
            {rows.map((c) => {
              const mine = c.mineYn === 'Y'
              return (
                <div key={c.dbKey} style={{ display: 'flex', flexDirection: mine ? 'row-reverse' : 'row', gap: 8 }}>
                  <UserAvatar fileId={c.regProfileFileId} name={c.regNm} handle={c.regHandle} size={28} showName={false} />
                  <div style={{ maxWidth: '78%' }}>
                    <div style={{
                      display: 'flex', gap: 6, alignItems: 'center', marginBottom: 2,
                      flexDirection: mine ? 'row-reverse' : 'row',
                    }}>
                      <span style={{ fontSize: 12, fontWeight: 600 }}>{c.regNm || '회원'}</span>
                      {c.hostYn === 'Y' && <Tag color="purple" style={{ margin: 0, fontSize: 11 }}>주최자</Tag>}
                      <span style={{ fontSize: 11, color: gen.inkFaint }}>{c.regDt}</span>
                      {mine && (
                        <Popconfirm title="이 대화를 삭제할까요?" onConfirm={() => remove(c.dbKey!)} okText="삭제" cancelText="취소">
                          <a style={{ fontSize: 11, color: gen.inkFaint }}>삭제</a>
                        </Popconfirm>
                      )}
                    </div>
                    <div style={{
                      background: mine ? gen.primary : '#fff',
                      color: mine ? '#fff' : gen.ink,
                      border: mine ? 'none' : `1px solid ${gen.line}`,
                      borderRadius: 12, padding: '7px 11px', whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                    }}>
                      {c.content}
                    </div>
                  </div>
                </div>
              )
            })}
          </Space>
        )}
      </div>

      <Space.Compact style={{ width: '100%', marginTop: 10 }}>
        <Input
          placeholder="멤버에게 남길 말 (준비물, 만날 장소 등)" value={text} maxLength={1000}
          onChange={(e) => setText(e.target.value)} onPressEnter={send} disabled={sending}
        />
        <Button type="primary" onClick={send} loading={sending}>보내기</Button>
      </Space.Compact>
    </div>
  )
}
