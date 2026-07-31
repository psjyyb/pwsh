import { Select } from 'antd'

interface Props {
  value?: string
  onChange?: (v: string) => void
  width?: number
}

/** 사용/미사용(Y/N) 선택. Form에는 'Y'/'N' 문자열로 저장.
 *   <Form.Item name="fileYn"><YnSelect /></Form.Item> */
export default function YnSelect({ value, onChange, width = 120 }: Props) {
  return (
    <Select
      value={value}
      onChange={onChange}
      style={{ width }}
      options={[
        { value: 'Y', label: '사용' },
        { value: 'N', label: '미사용' },
      ]}
    />
  )
}
