import { Select } from 'antd'
import type { SelectProps } from 'antd'
import { useCodes } from '../../hooks/useCodes'

interface Props extends Omit<SelectProps, 'options'> {
  /** 공통코드 그룹 ID (예: MEM00, STATUS00, POP00) */
  pCodeId: string
  /** 맨 앞에 "전체" 옵션 추가 */
  allowAll?: boolean
}

/**
 * 공통코드 기반 Select. Form.Item 안에 넣으면 value/onChange 자동 연동.
 *   <Form.Item name="typeCd"><CodeSelect pCodeId="MEM00" /></Form.Item>
 */
export default function CodeSelect({ pCodeId, allowAll, placeholder = '선택', ...rest }: Props) {
  const options = useCodes(pCodeId)
  const merged = allowAll ? [{ value: '', label: '전체' }, ...options] : options
  return <Select options={merged} placeholder={placeholder} {...rest} />
}
