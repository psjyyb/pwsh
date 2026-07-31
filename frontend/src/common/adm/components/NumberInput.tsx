import { Input } from 'antd'
import { formatNumber, unformatNumber } from '../../util/format'

interface Props {
  /** 저장 값(숫자만). Form.Item이 주입 */
  value?: string
  onChange?: (v: string) => void
  placeholder?: string
  disabled?: boolean
}

/**
 * 숫자 입력기 — 화면에는 천단위 콤마(1,000,000), Form/DB에는 숫자만(1000000) 저장.
 * 입력 중 자동 콤마, 조회 시 저장된 숫자를 포맷해서 표시.
 *   <Form.Item name="amount"><NumberInput /></Form.Item>
 */
export default function NumberInput({ value, onChange, placeholder, disabled }: Props) {
  return (
    <Input
      value={formatNumber(value)}
      onChange={(e) => onChange?.(unformatNumber(e.target.value))}
      placeholder={placeholder}
      disabled={disabled}
      inputMode="numeric"
    />
  )
}
