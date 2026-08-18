import { gen } from '../../../gen/theme'

/** 본문에서 @닉네임을 찾아 강조한다(한글·영문·숫자·밑줄, 최대 30자 = 백엔드 파서와 같은 규칙). */
const MENTION = /@([\p{L}\p{N}_]{1,30})/gu

/**
 * 댓글·대화 본문 표시 — @닉네임만 색으로 강조한다.
 *
 * innerHTML을 쓰지 않고 조각(fragment)으로 만들어 붙인다. 본문은 사용자 입력이라
 * HTML로 해석되면 그대로 스크립트 삽입 통로가 되기 때문이다.
 */
export default function MentionText({ text }: { text?: string }) {
  if (!text) return null
  const parts: Array<string | { name: string }> = []
  let last = 0
  for (const m of text.matchAll(MENTION)) {
    const at = m.index ?? 0
    if (at > last) parts.push(text.slice(last, at))
    parts.push({ name: m[1] })
    last = at + m[0].length
  }
  if (last < text.length) parts.push(text.slice(last))

  return (
    <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
      {parts.map((p, i) =>
        typeof p === 'string'
          ? <span key={i}>{p}</span>
          : <b key={i} style={{ color: gen.primary }}>@{p.name}</b>,
      )}
    </span>
  )
}
