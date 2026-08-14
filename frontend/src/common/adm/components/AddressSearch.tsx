import { useCallback } from 'react'
import { Button, message } from 'antd'

/**
 * 다음(카카오) 우편번호 검색 버튼 (주소 입력이 필요한 화면에서 재사용).
 * 외부 스크립트(postcode.v2.js)를 1회 동적 로드 후 팝업으로 주소 선택 → onSelect 콜백.
 * 사용 예)
 *   const [form] = Form.useForm()
 *   <AddressSearch onSelect={(r) => form.setFieldsValue({ zipCode: r.zonecode, addr1: r.address })} />
 */
const SCRIPT_SRC = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'

export interface AddressResult {
  zonecode: string // 우편번호(5자리)
  address: string // 선택 유형(도로명/지번)에 맞춘 주소
  buildingName: string // 건물명
  addressType: 'R' | 'J' // R=도로명, J=지번
}

interface Props {
  onSelect: (result: AddressResult) => void
  buttonText?: string
  disabled?: boolean
}

let scriptLoading: Promise<void> | null = null

/** postcode.v2.js 1회 로드 (이미 로드됐으면 즉시 resolve) */
function loadPostcodeScript(): Promise<void> {
  if (typeof window !== 'undefined' && (window as any).daum?.Postcode) return Promise.resolve()
  if (scriptLoading) return scriptLoading
  scriptLoading = new Promise<void>((resolve, reject) => {
    const s = document.createElement('script')
    s.src = SCRIPT_SRC
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => {
      scriptLoading = null
      reject(new Error('우편번호 스크립트 로드 실패'))
    }
    document.head.appendChild(s)
  })
  return scriptLoading
}

export default function AddressSearch({ onSelect, buttonText = '주소 검색', disabled }: Props) {
  const open = useCallback(() => {
    loadPostcodeScript()
      .then(() => {
        new (window as any).daum.Postcode({
          oncomplete: (data: any) => {
            const address = data.userSelectedType === 'J' ? data.jibunAddress : data.roadAddress
            onSelect({
              zonecode: data.zonecode,
              address,
              buildingName: data.buildingName ?? '',
              addressType: data.userSelectedType,
            })
          },
        }).open()
      })
      .catch((e) => message.error(e instanceof Error ? e.message : '주소 검색을 열 수 없습니다.'))
  }, [onSelect])

  return (
    <Button onClick={open} disabled={disabled}>
      {buttonText}
    </Button>
  )
}
