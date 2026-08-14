import { useEffect, useRef, useState } from 'react'
import { tokenStore } from '../../auth/token'

/**
 * 서버 푸시(SSE) 수신 훅.
 *
 * EventSource를 쓰지 않는다 — EventSource는 GET만 되고 Authorization 헤더를 실을 수 없어
 * 토큰을 URL에 넣어야 하고, 그러면 접근 로그·리퍼러에 토큰이 남는다.
 * 그래서 fetch(POST + 헤더)로 스트림을 직접 읽는다. 대신 자동 재연결을 여기서 구현한다.
 *
 * 이벤트에는 본문이 없다("새 게 있다"만 알림) — 실제 데이터는 호출자가 기존 조회 API로 가져간다.
 *
 * @param url    스트림 엔드포인트(예: /api/adm/message/selectMessageListStream.do)
 * @param onEvent 이벤트 수신 콜백(event 이름). 최신 콜백이 항상 쓰이도록 내부에서 ref로 보관한다.
 * @returns connected — 스트림이 살아있는지. false면 호출자가 폴링으로 대체하면 된다.
 */
export function useEventStream(url: string, onEvent: (event: string) => void): boolean {
  const [connected, setConnected] = useState(false)
  const cbRef = useRef(onEvent)
  cbRef.current = onEvent

  useEffect(() => {
    if (!tokenStore.get()) return
    let stopped = false
    let controller: AbortController | null = null
    let retryTimer: number | undefined
    let attempt = 0

    const connect = async () => {
      if (stopped) return
      controller = new AbortController()
      try {
        const res = await fetch(url, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${tokenStore.get()}`,
            Accept: 'text/event-stream',
          },
          signal: controller.signal,
        })
        if (!res.ok || !res.body) throw new Error(`stream ${res.status}`)

        setConnected(true)
        attempt = 0
        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buf = ''

        for (;;) {
          const { value, done } = await reader.read()
          if (done || stopped) break
          buf += decoder.decode(value, { stream: true })

          // SSE는 빈 줄로 이벤트가 끊긴다. 남은 조각은 버퍼에 두고 다음 청크와 이어붙인다.
          const chunks = buf.split('\n\n')
          buf = chunks.pop() ?? ''
          for (const chunk of chunks) {
            const line = chunk.split('\n').find((l) => l.startsWith('event:'))
            const name = line ? line.slice('event:'.length).trim() : ''
            // ready(연결 확인)와 주석(:ping)은 호출자에게 전달하지 않는다
            if (name && name !== 'ready') cbRef.current(name)
          }
        }
      } catch {
        /* 네트워크 끊김·서버 재시작 등 — 아래에서 재연결 */
      } finally {
        setConnected(false)
        if (!stopped) {
          // 지수 백오프(1s → 최대 30s): 서버가 죽었을 때 재연결로 몰아치지 않도록
          attempt += 1
          const delay = Math.min(30000, 1000 * 2 ** Math.min(attempt - 1, 5))
          retryTimer = window.setTimeout(connect, delay)
        }
      }
    }

    connect()
    return () => {
      stopped = true
      if (retryTimer) window.clearTimeout(retryTimer)
      controller?.abort()
    }
  }, [url])

  return connected
}
