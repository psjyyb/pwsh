import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Space, Typography } from 'antd'
import { hasMapKey, kakaoMapLink, loadKakaoMap } from '../../util/kakaoMap'
import { gen } from '../../../gen/theme'

interface Props {
  placeNm?: string
  addr?: string
  lat?: string
  lng?: string
  height?: number
}

/**
 * 모임 장소 지도(읽기 전용) — 저장된 좌표에 마커 하나를 찍는다.
 * 좌표가 없으면 지도를 그리지 않고 장소명·주소만 보여준다(온라인·장소 미정 모임).
 * 키가 없거나 로드에 실패하면 지도 대신 사유를 보여주고 카카오맵 링크는 계속 제공한다
 * (링크는 SDK·키 없이 동작하므로 지도가 죽어도 길찾기는 살아 있다).
 */
export default function PlaceMap({ placeNm, addr, lat, lng, height = 220 }: Props) {
  const boxRef = useRef<HTMLDivElement>(null)
  const [error, setError] = useState<string | null>(null)
  const hasCoord = !!(lat && lng)

  useEffect(() => {
    if (!hasCoord || !hasMapKey) return
    let disposed = false
    loadKakaoMap()
      .then(() => {
        if (disposed || !boxRef.current) return
        const kakao = (window as any).kakao
        const center = new kakao.maps.LatLng(Number(lat), Number(lng))
        const map = new kakao.maps.Map(boxRef.current, { center, level: 4 })
        const marker = new kakao.maps.Marker({ position: center })
        marker.setMap(map)
        if (placeNm) {
          new kakao.maps.InfoWindow({
            content: `<div style="padding:5px 8px;font-size:12px;white-space:nowrap">${placeNm}</div>`,
          }).open(map, marker)
        }
        // 컨테이너가 숨어 있다가 보이면 지도가 회색으로 남는다 → 다시 그려준다
        setTimeout(() => map.relayout(), 0)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : '지도를 불러올 수 없습니다.'))
    return () => { disposed = true }
  }, [hasCoord, lat, lng, placeNm])

  if (!placeNm && !addr && !hasCoord) {
    return <span style={{ color: gen.inkFaint }}>장소 미정</span>
  }

  return (
    <Space direction="vertical" size={8} style={{ width: '100%' }}>
      <Space size={8} wrap>
        {placeNm && <b>{placeNm}</b>}
        {addr && <Typography.Text type="secondary" copyable style={{ fontSize: 13 }}>{addr}</Typography.Text>}
        <Button size="small" href={kakaoMapLink(placeNm, lat, lng, addr)} target="_blank" rel="noreferrer">
          카카오맵으로 열기
        </Button>
      </Space>

      {hasCoord && !hasMapKey && (
        <Alert type="info" showIcon message="지도 키가 설정되지 않아 지도를 표시하지 않습니다. 위 링크로 열어 보세요." />
      )}
      {hasCoord && hasMapKey && error && <Alert type="warning" showIcon message={error} />}
      {hasCoord && hasMapKey && !error && (
        <div ref={boxRef} style={{ width: '100%', height, borderRadius: 10, border: `1px solid ${gen.line}` }} />
      )}
    </Space>
  )
}
