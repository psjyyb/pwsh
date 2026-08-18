import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Input, List, Modal, Space, Tag, Typography, message } from 'antd'
import { hasMapKey, loadKakaoMap } from '../../util/kakaoMap'
import { gen } from '../../../gen/theme'

export interface PickedPlace {
  placeNm: string
  addr: string
  lat: string
  lng: string
}

interface Props {
  value?: Partial<PickedPlace>
  onChange?: (v: PickedPlace | undefined) => void
}

interface Candidate extends PickedPlace {
  id: string
  category?: string
}

/**
 * 모임 장소 선택 — 카카오 장소 검색으로 찾고, 지도에서 확인한 뒤 좌표까지 저장한다.
 *
 * <p>Form.Item 안에서 value/onChange로 동작한다(AntD 커스텀 입력 규약).
 * 좌표를 직접 입력받지 않는 이유: 사람이 손으로 넣으면 틀린 지점에 마커가 찍히고, 그걸 보고
 * 엉뚱한 곳으로 모이게 된다. 반드시 검색 결과에서 고른 좌표만 저장한다.
 */
export default function PlacePicker({ value, onChange }: Props) {
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [rows, setRows] = useState<Candidate[]>([])
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sel, setSel] = useState<Candidate | null>(null)
  const mapRef = useRef<HTMLDivElement>(null)
  const mapObj = useRef<any>(null)
  const markerObj = useRef<any>(null)

  /**
   * 지도 생성/복구 — 모달이 <b>완전히 열린 뒤</b>(afterOpenChange)에만 호출한다.
   * 열리는 애니메이션 중에 만들면 컨테이너 크기가 0으로 측정돼 타일이 안 그려지고 회색으로 남는다.
   * 이미 만들어 둔 지도가 있으면 재생성하지 않고 relayout으로 크기만 다시 잡는다(모달 재사용 대비).
   */
  const initMap = useCallback(async (): Promise<any> => {
    if (!hasMapKey) return null
    try {
      await loadKakaoMap()
    } catch (e) {
      setError(e instanceof Error ? e.message : '지도를 불러올 수 없습니다.')
      return null
    }
    if (!mapRef.current) return null
    const kakao = (window as any).kakao
    const start = value?.lat && value?.lng
      ? new kakao.maps.LatLng(Number(value.lat), Number(value.lng))
      : new kakao.maps.LatLng(37.5665, 126.9780) // 좌표가 없을 때의 초기 화면(서울시청)
    if (mapObj.current) {
      mapObj.current.relayout()
      return mapObj.current
    }
    mapObj.current = new kakao.maps.Map(mapRef.current, { center: start, level: 4 })
    markerObj.current = new kakao.maps.Marker({ position: start })
    if (value?.lat && value?.lng) markerObj.current.setMap(mapObj.current)
    mapObj.current.relayout()
    return mapObj.current
  }, [value?.lat, value?.lng])

  // 모달을 닫으면 다음 열기에서 새 컨테이너에 다시 만들 수 있게 참조를 버린다
  useEffect(() => {
    if (!open) {
      mapObj.current = null
      markerObj.current = null
    }
  }, [open])

  /** 지도 중심·마커를 고른 장소로 이동. 지도가 아직 없으면 먼저 만든다(초기화 타이밍에 좌우되지 않게). */
  const focus = useCallback(async (c: Candidate) => {
    setSel(c)
    const map = mapObj.current ?? await initMap()
    if (!map) return
    const kakao = (window as any).kakao
    const pos = new kakao.maps.LatLng(Number(c.lat), Number(c.lng))
    map.setCenter(pos)
    markerObj.current?.setPosition(pos)
    markerObj.current?.setMap(map)
  }, [initMap])

  const search = async () => {
    const kw = keyword.trim()
    if (!kw) return
    setSearching(true)
    setError(null)
    try {
      await loadKakaoMap()
      const kakao = (window as any).kakao
      const places = new kakao.maps.services.Places()
      places.keywordSearch(kw, (data: any[], status: string) => {
        setSearching(false)
        if (status !== kakao.maps.services.Status.OK) {
          setRows([])
          if (status === kakao.maps.services.Status.ZERO_RESULT) {
            setError('검색 결과가 없습니다. 장소명이나 주소로 다시 찾아보세요.')
          } else {
            setError('장소 검색에 실패했습니다.')
          }
          return
        }
        const list: Candidate[] = data.map((d) => ({
          id: d.id,
          placeNm: d.place_name,
          addr: d.road_address_name || d.address_name,
          lat: String(d.y),
          lng: String(d.x),
          category: d.category_group_name || undefined,
        }))
        setRows(list)
        if (list.length > 0) focus(list[0])
      })
    } catch (e) {
      setSearching(false)
      setError(e instanceof Error ? e.message : '지도를 불러올 수 없습니다.')
    }
  }

  const confirm = () => {
    if (!sel) {
      message.warning('목록에서 장소를 선택해 주세요.')
      return
    }
    onChange?.({ placeNm: sel.placeNm, addr: sel.addr, lat: sel.lat, lng: sel.lng })
    setOpen(false)
  }

  const clear = () => {
    onChange?.(undefined)
    setSel(null)
    setRows([])
    setKeyword('')
  }

  return (
    <>
      <Space size={8} wrap>
        {value?.placeNm ? (
          <>
            <Tag color="purple" style={{ margin: 0 }}>{value.placeNm}</Tag>
            {value.addr && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{value.addr}</Typography.Text>}
            <Button size="small" onClick={() => setOpen(true)}>변경</Button>
            <Button size="small" onClick={clear}>지우기</Button>
          </>
        ) : (
          <>
            <Button onClick={() => setOpen(true)}>장소 찾기</Button>
            <span style={{ color: gen.inkFaint, fontSize: 12 }}>선택 안 하면 장소 미정으로 남습니다</span>
          </>
        )}
      </Space>

      <Modal
        title="모임 장소 찾기"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={confirm}
        okText="이 장소로"
        cancelText="취소"
        width={720}
        destroyOnHidden
        afterOpenChange={(opened) => { if (opened) initMap() }}
      >
        {!hasMapKey ? (
          <Alert
            type="warning" showIcon
            message="지도 키가 설정되지 않았습니다."
            description="frontend/.env 의 VITE_KAKAO_MAP_KEY 를 설정하고 Kakao Developers에 사이트 도메인을 등록해야 장소 검색이 동작합니다."
          />
        ) : (
          <Space direction="vertical" size={10} style={{ width: '100%' }}>
            <Space.Compact style={{ width: '100%' }}>
              <Input
                placeholder="장소명 또는 주소 (예: 강남역 11번 출구, 북한산 우이역)"
                value={keyword} onChange={(e) => setKeyword(e.target.value)}
                onPressEnter={search} allowClear
              />
              <Button type="primary" onClick={search} loading={searching}>검색</Button>
            </Space.Compact>

            {error && <Alert type="info" showIcon message={error} />}

            <div ref={mapRef} style={{ width: '100%', height: 260, borderRadius: 10, border: `1px solid ${gen.line}` }} />

            {rows.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="검색해서 장소를 골라 주세요." />
            ) : (
              <List
                size="small" bordered style={{ maxHeight: 200, overflowY: 'auto' }}
                dataSource={rows}
                renderItem={(c) => (
                  <List.Item
                    onClick={() => focus(c)}
                    style={{
                      cursor: 'pointer',
                      background: sel?.id === c.id ? gen.surfaceAlt : undefined,
                    }}
                  >
                    <Space direction="vertical" size={0}>
                      <Space size={6}>
                        <b>{c.placeNm}</b>
                        {c.category && <Tag style={{ margin: 0 }}>{c.category}</Tag>}
                      </Space>
                      <span style={{ fontSize: 12, color: gen.inkFaint }}>{c.addr}</span>
                    </Space>
                  </List.Item>
                )}
              />
            )}
          </Space>
        )}
      </Modal>
    </>
  )
}
