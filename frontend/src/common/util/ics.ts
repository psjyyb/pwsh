/**
 * 일정을 .ics(iCalendar)로 만들어 내려받는다 — 구글/애플/아웃룩 캘린더가 공통으로 읽는 형식.
 *
 * 서버 없이 브라우저에서 만든다. 모임 정보(제목·날짜·장소·링크)는 화면이 이미 갖고 있어서
 * 굳이 API를 새로 열 필요가 없고, 그만큼 인증·인가 표면도 늘지 않는다.
 *
 * 모임 일정(meetDt)은 시간이 없는 날짜라 **종일 일정**으로 만든다(DTSTART;VALUE=DATE).
 */

export interface IcsEvent {
  /** 일정 고유 키(모집 id 등) — 같은 일정을 다시 담으면 캘린더가 중복 대신 갱신한다 */
  uid: string
  title: string
  /** YYYY-MM-DD */
  date: string
  location?: string
  description?: string
  /** 상세 페이지 링크(캘린더에서 눌러 돌아올 수 있게) */
  url?: string
}

/** iCalendar TEXT 이스케이프: 역슬래시·세미콜론·콤마·개행. */
function esc(v: string): string {
  return v.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n')
}

/** YYYY-MM-DD → YYYYMMDD */
function ymd(date: string): string {
  return date.replace(/-/g, '')
}

/**
 * 종일 일정의 끝날짜는 '다음 날'(DTEND는 미포함 경계).
 * toISOString()은 UTC로 바꿔버려서 KST 자정이 전날로 밀린다 → 로컬 날짜 필드로 직접 만든다.
 */
function nextDay(date: string): string {
  const d = new Date(`${date}T00:00:00`)
  d.setDate(d.getDate() + 1)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}${mm}${dd}`
}

/** RFC 5545 권장: 75옥텟마다 접기(줄 앞 공백 1칸). 긴 제목·설명에서 깨지는 클라이언트 대비. */
function fold(line: string): string {
  if (line.length <= 74) return line
  const parts: string[] = []
  let rest = line
  parts.push(rest.slice(0, 74))
  rest = rest.slice(74)
  while (rest.length > 0) {
    parts.push(' ' + rest.slice(0, 73))
    rest = rest.slice(73)
  }
  return parts.join('\r\n')
}

function toVevent(e: IcsEvent, stamp: string): string[] {
  const lines = [
    'BEGIN:VEVENT',
    `UID:${e.uid}`,
    `DTSTAMP:${stamp}`,
    `DTSTART;VALUE=DATE:${ymd(e.date)}`,
    `DTEND;VALUE=DATE:${nextDay(e.date)}`,
    `SUMMARY:${esc(e.title)}`,
  ]
  if (e.location) lines.push(`LOCATION:${esc(e.location)}`)
  if (e.description) lines.push(`DESCRIPTION:${esc(e.description)}`)
  if (e.url) lines.push(`URL:${esc(e.url)}`)
  lines.push('END:VEVENT')
  return lines
}

/** 이벤트 목록을 .ics 텍스트로 (CRLF 개행 필수). */
export function buildIcs(events: IcsEvent[]): string {
  const stamp = new Date().toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z'
  const body = events.flatMap((e) => toVevent(e, stamp))
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//pwsh//hobby//KO', 'CALSCALE:GREGORIAN', ...body, 'END:VCALENDAR']
  return lines.map(fold).join('\r\n') + '\r\n'
}

/** .ics 파일로 내려받기. */
export function downloadIcs(events: IcsEvent[], fileName = 'schedule.ics'): void {
  const blob = new Blob([buildIcs(events)], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/** 파일명에 쓸 수 없는 문자 정리(윈도우 기준). */
export function safeFileName(name: string): string {
  return (name || 'schedule').replace(/[\\/:*?"<>|]/g, '').trim().slice(0, 40) || 'schedule'
}
