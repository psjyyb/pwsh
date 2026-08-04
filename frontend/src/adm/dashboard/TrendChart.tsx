import { Tooltip } from 'antd'

/**
 * 단일 시리즈 막대 추이(작은 다중 카드용). 차트 라이브러리 없이 CSS만 사용.
 * - 시리즈가 1개라 범례 불필요(제목이 시리즈를 지칭)
 * - 막대 위 끝 4px 라운드 + 막대 사이 2px 간격, 축·격자는 최소(recessive)
 * - 값은 hover 툴팁 + 합계/최근값을 텍스트로 노출(표면 대비가 3:1 미만인 색의 보완 수단)
 */
export interface TrendPoint { label: string; value: number }

export default function TrendChart({
  points,
  color,
  unit = '건',
}: {
  points: TrendPoint[]
  color: string
  unit?: string
}) {
  const max = Math.max(1, ...points.map((p) => p.value))
  const total = points.reduce((s, p) => s + p.value, 0)
  const last = points.length ? points[points.length - 1].value : 0

  return (
    <div>
      {/* 요약 수치(직접 라벨) — 색만으로 정보를 전달하지 않도록 */}
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 10 }}>
        <span style={{ fontSize: 24, fontWeight: 700, color: '#1f1f1f' }}>{total.toLocaleString()}</span>
        <span style={{ fontSize: 12, color: '#8c8c8c' }}>최근 {points.length}일 합계 · 오늘 {last}{unit}</span>
      </div>

      {/* 막대 */}
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 2, height: 84, borderBottom: '1px solid #f0f0f0' }}>
        {points.map((p) => (
          <Tooltip key={p.label} title={`${p.label} · ${p.value}${unit}`}>
            <div style={{ flex: 1, display: 'flex', alignItems: 'flex-end', height: '100%', minWidth: 0 }}>
              <div
                style={{
                  width: '100%',
                  height: `${Math.max(p.value === 0 ? 2 : 6, Math.round((p.value / max) * 100))}%`,
                  background: p.value === 0 ? '#f0f0f0' : color,
                  borderRadius: '4px 4px 0 0',
                  transition: 'height .2s ease',
                }}
              />
            </div>
          </Tooltip>
        ))}
      </div>

      {/* x축: 처음/중간/끝만 (라벨 충돌 방지) */}
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#bfbfbf', marginTop: 4 }}>
        <span>{points[0]?.label}</span>
        <span>{points[Math.floor(points.length / 2)]?.label}</span>
        <span>{points[points.length - 1]?.label}</span>
      </div>
    </div>
  )
}
