import type { CSSProperties } from 'react'
import { DatePicker } from 'antd'
import dayjs from 'dayjs'
import customParseFormat from 'dayjs/plugin/customParseFormat'

dayjs.extend(customParseFormat)

/** 저장 포맷: 날짜=YYYY-MM-DD, 날짜+시간=YYYY-MM-DD HH:mm:ss (전 프로젝트 통일) */
const DATE_FMT = 'YYYY-MM-DD'
const DATETIME_FMT = 'YYYY-MM-DD HH:mm:ss'

interface Props {
  /** 저장 포맷 문자열(Form.Item이 주입). 예: '2026-07-21' */
  value?: string
  onChange?: (v: string) => void
  /** true면 시:분:초까지 입력/저장 */
  showTime?: boolean
  placeholder?: string
  allowClear?: boolean
  disabled?: boolean
  style?: CSSProperties
}

/**
 * 문자열 값을 다루는 날짜 선택기(Form.Item에 문자열로 저장). 내부적으로 dayjs 변환.
 *   <Form.Item name="birth"><DateField /></Form.Item>              // YYYY-MM-DD
 *   <Form.Item name="startDt"><DateField showTime /></Form.Item>   // YYYY-MM-DD HH:mm:ss
 * 레거시 값(YYYYMMDD 등)도 최대한 파싱해서 표시.
 */
export default function DateField({ value, onChange, showTime, style, ...rest }: Props) {
  const fmt = showTime ? DATETIME_FMT : DATE_FMT
  // 저장 포맷 우선, 실패 시 느슨하게(레거시 YYYYMMDD 등) 파싱
  let d = value ? dayjs(value, fmt, true) : null
  if (value && (!d || !d.isValid())) {
    const loose = dayjs(value)
    d = loose.isValid() ? loose : (dayjs(value, 'YYYYMMDD', true).isValid() ? dayjs(value, 'YYYYMMDD') : null)
  }
  return (
    <DatePicker
      {...rest}
      showTime={showTime}
      format={fmt}
      value={d && d.isValid() ? d : null}
      onChange={(m) => onChange?.(m ? m.format(fmt) : '')}
      style={{ width: '100%', ...style }}
    />
  )
}
