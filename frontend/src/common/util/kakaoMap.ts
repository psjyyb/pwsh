/**
 * 카카오(다음) 지도 SDK 로더.
 *
 * 키는 브라우저에 노출되는 게 정상인 JavaScript 키라 .env(VITE_KAKAO_MAP_KEY)로 주입한다.
 * 유일한 보호 장치는 Kakao Developers의 사이트 도메인 화이트리스트이므로, 새 도메인에 올릴 때마다
 * 콘솔에 등록해야 지도가 뜬다.
 *
 * autoload=false로 받아 kakao.maps.load()로 초기화를 명시적으로 기다린다(스크립트 onload 시점에는
 * 아직 maps 네임스페이스가 준비되지 않는다). libraries=services는 장소 키워드 검색에 필요하다.
 */
const KEY = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined

/** 키 미설정 여부 — 화면이 "지도 대신 안내"를 보여줄지 판단하는 데 쓴다. */
export const hasMapKey = !!KEY

let loading: Promise<void> | null = null

export function loadKakaoMap(): Promise<void> {
  if (!KEY) {
    return Promise.reject(new Error('지도 키(VITE_KAKAO_MAP_KEY)가 설정되지 않았습니다.'))
  }
  const w = window as any
  if (w.kakao?.maps?.services) {
    return Promise.resolve()
  }
  if (loading) {
    return loading
  }
  loading = new Promise<void>((resolve, reject) => {
    const s = document.createElement('script')
    s.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KEY}&libraries=services&autoload=false`
    s.async = true
    s.onload = () => {
      const kakao = (window as any).kakao
      if (!kakao?.maps) {
        loading = null
        reject(new Error('지도 SDK 초기화에 실패했습니다.'))
        return
      }
      kakao.maps.load(() => resolve())
    }
    s.onerror = () => {
      loading = null
      // 키가 틀렸거나 도메인이 등록되지 않으면 여기로 온다(브라우저 콘솔에 카카오 응답이 남는다)
      reject(new Error('지도를 불러올 수 없습니다. 키와 등록된 도메인을 확인해 주세요.'))
    }
    document.head.appendChild(s)
  })
  return loading
}

/** 카카오맵 웹으로 열기(길찾기) — SDK·키 없이 동작하는 외부 링크. */
export function kakaoMapLink(placeNm?: string, lat?: string, lng?: string, addr?: string): string {
  if (lat && lng) {
    // 좌표가 있으면 그 지점을 정확히 띄운다. 장소명은 라벨로만 쓰인다.
    return `https://map.kakao.com/link/map/${encodeURIComponent(placeNm || '모임 장소')},${lat},${lng}`
  }
  return `https://map.kakao.com/?q=${encodeURIComponent(addr || placeNm || '')}`
}
