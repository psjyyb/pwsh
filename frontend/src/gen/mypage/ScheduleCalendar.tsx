import { useMemo, useState } from 'react'
import { Button, Space, Tag } from 'antd'
import dayjs from 'dayjs'
import { gen } from '../theme'

/** 캘린더에 찍히는 일정 1건(마이페이지에서 주최/참여 모집을 합쳐 넘긴다). */
export interface ScheduleItem {
  key: string
  id: string        // 모집 id(클릭 시 이동)
  title: string
  meetDt: string    // YYYY-MM-DD
  region?: string
  role: '주최' | '참여'
}

const WEEK = ['일', '월', '화', '수', '목', '금', '토']
const CELLS = 42 // 6주 고정 — 달마다 높이가 바뀌면 아래 내용이 튄다

/**
 * 내 일정 월 캘린더. 데이터는 마이페이지가 이미 가진 모집 목록에서 만들어 넘긴다(추가 API 없음).
 *
 * AntD Calendar를 쓰지 않은 이유: 셀에 색 칩을 넣으려면 내부 스타일을 상당히 덮어써야 하고,
 * 필요한 건 '월 그리드 + 일정 칩'뿐이어서 직접 그리는 편이 디자인 토큰과도 맞는다.
 */
export default function ScheduleCalendar({
  items, onSelect,
}: { items: ScheduleItem[]; onSelect: (id: string) => void }) {
  const today = dayjs().format('YYYY-MM-DD')
  const [month, setMonth] = useState(() => dayjs().startOf('month'))
  const [picked, setPicked] = useState<string | null>(null)

  /** 날짜(YYYY-MM-DD) → 그 날의 일정들 */
  const byDate = useMemo(() => {
    const map = new Map<string, ScheduleItem[]>()
    for (const it of items) {
      const list = map.get(it.meetDt)
      if (list) list.push(it)
      else map.set(it.meetDt, [it])
    }
    return map
  }, [items])

  const start = month.startOf('month').startOf('week') // 일요일 시작
  const days = Array.from({ length: CELLS }, (_, i) => start.add(i, 'day'))
  const pickedItems = picked ? byDate.get(picked) ?? [] : []
  const monthCnt = items.filter((it) => it.meetDt.startsWith(month.format('YYYY-MM'))).length

  const chipColor = (role: ScheduleItem['role']) => (role === '주최' ? gen.primary : '#3B82F6')

  return (
    <div>
      {/* 월 이동 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        <Button size="small" onClick={() => { setMonth(month.subtract(1, 'month')); setPicked(null) }}>‹</Button>
        <span style={{ fontWeight: 700, minWidth: 110, textAlign: 'center' }}>{month.format('YYYY년 M월')}</span>
        <Button size="small" onClick={() => { setMonth(month.add(1, 'month')); setPicked(null) }}>›</Button>
        <Button size="small" type="link" onClick={() => { setMonth(dayjs().startOf('month')); setPicked(today) }}>오늘</Button>
        <span style={{ marginLeft: 'auto', fontSize: 12, color: gen.inkFaint }}>이 달 모임 {monthCnt}건</span>
      </div>

      {/* 요일 머리 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4, marginBottom: 4 }}>
        {WEEK.map((w, i) => (
          <div key={w} style={{
            textAlign: 'center', fontSize: 12, fontWeight: 600, padding: '2px 0',
            color: i === 0 ? '#E05252' : i === 6 ? '#3B82F6' : gen.inkSoft,
          }}>{w}</div>
        ))}
      </div>

      {/* 날짜 그리드 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4 }}>
        {days.map((d) => {
          const ymd = d.format('YYYY-MM-DD')
          const dayItems = byDate.get(ymd) ?? []
          const outside = d.month() !== month.month()
          const isToday = ymd === today
          const isPicked = ymd === picked
          return (
            <div
              key={ymd}
              onClick={() => setPicked(dayItems.length ? ymd : null)}
              style={{
                minHeight: 62, borderRadius: 10, padding: '4px 5px',
                background: isPicked ? gen.heroTint : outside ? '#FCFBFF' : gen.surface,
                border: `1px solid ${isToday ? gen.primary : gen.line}`,
                opacity: outside ? 0.55 : 1,
                cursor: dayItems.length ? 'pointer' : 'default',
              }}
            >
              <div style={{
                fontSize: 12, fontWeight: isToday ? 800 : 500, marginBottom: 2,
                color: isToday ? gen.primary : d.day() === 0 ? '#E05252' : d.day() === 6 ? '#3B82F6' : gen.ink,
              }}>{d.date()}</div>
              {dayItems.slice(0, 2).map((it) => (
                <div
                  key={it.key}
                  onClick={(e) => { e.stopPropagation(); onSelect(it.id) }}
                  title={`[${it.role}] ${it.title}`}
                  style={{
                    background: chipColor(it.role), color: '#fff', borderRadius: 6,
                    fontSize: 11, lineHeight: '15px', padding: '0 4px', marginBottom: 2,
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', cursor: 'pointer',
                  }}
                >{it.title}</div>
              ))}
              {dayItems.length > 2 && (
                <div style={{ fontSize: 10, color: gen.inkFaint }}>+{dayItems.length - 2}</div>
              )}
            </div>
          )
        })}
      </div>

      {/* 선택한 날의 일정 — 칩만으로는 지역·역할을 다 보여줄 수 없다 */}
      {picked && pickedItems.length > 0 && (
        <div style={{ marginTop: 12, borderTop: `1px solid ${gen.line}`, paddingTop: 10 }}>
          {/* 요일은 WEEK 배열로 — dayjs 한국어 로케일 로드에 의존하지 않는다(로드 안 되면 'Sat'으로 나온다) */}
          <div style={{ fontWeight: 700, marginBottom: 6 }}>
            {dayjs(picked).format('M월 D일')} ({WEEK[dayjs(picked).day()]})
          </div>
          <Space direction="vertical" size={6} style={{ width: '100%' }}>
            {pickedItems.map((it) => (
              <div key={it.key} onClick={() => onSelect(it.id)}
                style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', flexWrap: 'wrap' }}>
                <Tag color={it.role === '주최' ? 'purple' : 'blue'} style={{ flexShrink: 0 }}>{it.role}</Tag>
                <span style={{ fontWeight: 600 }}>{it.title}</span>
                {it.region && <span style={{ fontSize: 12, color: gen.inkFaint }}>{it.region}</span>}
              </div>
            ))}
          </Space>
        </div>
      )}
    </div>
  )
}
