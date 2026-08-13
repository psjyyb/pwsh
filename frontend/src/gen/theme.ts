import type { ThemeConfig } from 'antd'

/** 앱 공통 폰트 스택 — Pretendard(부드러운 라운드) 우선, 실패 시 시스템 폰트 폴백. */
export const fontStack =
  "'Pretendard Variable', Pretendard, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Malgun Gothic', Roboto, sans-serif"

/**
 * 사용자(gen) 영역 색상 팔레트 — 퍼플/라운드(관리자와 확연히 다른 캐주얼 톤).
 * index.css의 CSS 변수(--gen-*)와 같은 값을 쓴다. 인라인 style에서 쓰려면 이 객체를,
 * CSS/클래스에서 쓰려면 변수를 참조한다(둘 중 하나만 바꾸면 어긋나므로 함께 수정).
 */
export const gen = {
  primary: '#6C4EE3',   // 메인 퍼플(포인트)
  primaryDeep: '#2B2057', // 아주 진한 인디고(거의 미사용)
  accentText: '#4A3AA8', // 밝은 배경 위 강조 텍스트
  heroTint: '#EDE7FF',   // 연한 퍼플 틴트(히어로/뱃지)
  heroText: '#3B2E86',   // 연한 히어로 위 텍스트
  pageBg: '#EAE3FA',     // 본문 배경(연라벤더 — 기존보다 살짝 진하게)
  headerBg: '#F7F4FF',   // 헤더 배경(본문보다 밝은 라벤더)
  ink: '#211B3D',        // 본문 텍스트(순검정 대신 퍼플 편향 잉크)
  inkSoft: '#6B6392',    // 보조 텍스트
  inkFaint: '#9A94B8',   // 흐린 텍스트(메타 정보)
  line: '#E7E1F7',       // 경계선(회색 대신 라벤더 편향)
  surface: '#FFFFFF',    // 카드 표면
  surfaceAlt: '#FAF8FF', // 서브 표면(호버·강조 배경)
}

/**
 * 취미 카드 색상 팔레트 — 취미 1개 = 색 1개(카드를 그 색으로 채운다).
 *
 * 배정 기준은 hobbyId다. 순번(index % n)으로 돌리면 취미가 추가되거나 정렬이 바뀔 때
 * 모든 색이 한 칸씩 밀리고, 같은 취미가 화면마다 다른 색으로 보인다.
 * 색 수는 취미 수(현재 8)와 맞춰 인접 중복을 줄였다.
 */
const hobbyPalette = [
  { from: '#35D6A2', to: '#12B586' }, // green
  { from: '#A98BFA', to: '#7C5CF0' }, // purple
  { from: '#46BEF8', to: '#4F7BF7' }, // blue
  { from: '#FBBF3C', to: '#F59321' }, // amber
  { from: '#FB84AE', to: '#F45C8C' }, // pink
  { from: '#2FD3E6', to: '#08AEC9' }, // cyan
  { from: '#7C8CFF', to: '#4F5BD5' }, // indigo
  { from: '#FF9A76', to: '#F4693B' }, // coral
  { from: '#A8E063', to: '#56AB2F' }, // lime
  { from: '#E879F9', to: '#C026D3' }, // magenta
  { from: '#4FD1C5', to: '#0D9488' }, // teal
  { from: '#E0B973', to: '#B4802B' }, // mustard
]

/**
 * 취미 색 조회 — 같은 hobbyId면 어느 화면에서든 같은 색.
 * gradient는 카드 배경, solid는 아바타·점 같은 단색 자리에 쓴다.
 */
export function hobbyColor(hobbyId?: string): { gradient: string; solid: string } {
  const key = hobbyId ?? ''
  let h = 0
  for (let i = 0; i < key.length; i++) {
    h = (h * 31 + key.charCodeAt(i)) % 100000
  }
  const p = hobbyPalette[h % hobbyPalette.length]
  return { gradient: `linear-gradient(135deg, ${p.from} 0%, ${p.to} 100%)`, solid: p.to }
}

/** 취미 카드용 밝은 그라데이션 — 썸네일 자리(아바타) 배경 등에 사용. */
export const cardGradients = [
  'linear-gradient(135deg, #35D6A2 0%, #12B586 100%)', // green
  'linear-gradient(135deg, #A98BFA 0%, #7C5CF0 100%)', // purple
  'linear-gradient(135deg, #46BEF8 0%, #4F7BF7 100%)', // blue
  'linear-gradient(135deg, #FBBF3C 0%, #F59321 100%)', // amber
  'linear-gradient(135deg, #FB84AE 0%, #F45C8C 100%)', // pink
  'linear-gradient(135deg, #2FD3E6 0%, #08AEC9 100%)', // cyan
]

/**
 * 사용자(gen) 영역 AntD 테마. 관리자와 다른 스타일(퍼플·큰 라운드)을 여기서 관리.
 * 텍스트/경계 색까지 토큰으로 지정해 AntD 기본 회색이 브랜드와 겉돌지 않게 한다.
 */
export const genTheme: ThemeConfig = {
  token: {
    colorPrimary: gen.primary,
    colorLink: gen.primary,
    colorLinkHover: gen.accentText,
    colorText: gen.ink,
    colorTextSecondary: gen.inkSoft,
    colorTextTertiary: gen.inkFaint,
    colorTextDescription: gen.inkSoft,
    colorBorder: gen.line,
    colorBorderSecondary: gen.line,
    colorSplit: gen.line,
    colorBgLayout: gen.pageBg,
    borderRadius: 14,
    fontSize: 15,
    fontFamily: fontStack,
    lineHeight: 1.6,
    // 그림자를 퍼플 틴트로 — 무채색 그림자는 라벤더 배경 위에서 탁해 보인다
    boxShadow: '0 1px 2px rgba(33,27,61,.04), 0 2px 8px rgba(108,78,227,.06)',
    boxShadowSecondary: '0 2px 4px rgba(33,27,61,.05), 0 10px 24px rgba(108,78,227,.12)',
  },
  components: {
    Card: { borderRadiusLG: 20, paddingLG: 22 },
    Button: { controlHeight: 38, fontWeight: 500, primaryShadow: 'none' }, // 600은 과해서 500(부드럽게)
    Tag: { borderRadiusSM: 10 },
    Table: { headerBg: gen.surfaceAlt, headerColor: gen.inkSoft, rowHoverBg: gen.surfaceAlt, borderColor: gen.line },
    Input: { activeShadow: `0 0 0 3px ${gen.primary}1f` },
    Select: { optionSelectedBg: gen.heroTint },
    Segmented: { itemSelectedBg: gen.primary, itemSelectedColor: '#fff' },
    Modal: { borderRadiusLG: 20 },
    Descriptions: { labelBg: gen.surfaceAlt },
    Pagination: { itemActiveBg: gen.primary },
  },
}
