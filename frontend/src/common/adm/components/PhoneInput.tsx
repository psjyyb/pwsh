import { Input } from 'antd'
import { formatPhone, unformatPhone } from '../../util/format'

interface Props {
  /** 저장 값(숫자만). Form.Item이 주입 */
  value?: string
  onChange?: (v: string) => void
  placeholder?: string
  disabled?: boolean
}

/**
 * 전화번호 입력기 — 화면에는 `010-0000-0000` 하이픈으로 보이고, Form/DB에는 숫자만(01000000000) 저장.
 * 입력 중 자동 하이픈, 조회 시 저장된 숫자를 포맷해서 표시.
 *   <Form.Item name="phone"><PhoneInput /></Form.Item>
 */
export default function PhoneInput({ value, onChange, placeholder = '010-0000-0000', disabled }: Props) {
  return (
    <Input
      value={formatPhone(value)}
      onChange={(e) => onChange?.(unformatPhone(e.target.value))}
      placeholder={placeholder}
      disabled={disabled}
      maxLength={13}
      inputMode="numeric"
    />
  )
}
