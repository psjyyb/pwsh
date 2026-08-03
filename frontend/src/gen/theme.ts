import type { ThemeConfig } from 'antd'

/** 사용자(gen) 영역 색상 팔레트 — 퍼플/라운드(관리자와 확연히 다른 캐주얼 톤). */
export const gen = {
  primary: '#6C4EE3',   // 메인 퍼플(포인트)
  primaryDeep: '#2B2057', // 아주 진한 인디고(거의 미사용)
  accentText: '#4A3AA8', // 밝은 배경 위 강조 텍스트
  heroTint: '#EDE7FF',   // 연한 퍼플 틴트(히어로/뱃지)
  heroText: '#3B2E86',   // 연한 히어로 위 텍스트
  pageBg: '#EAE3FA',     // 본문 배경(연라벤더 — 기존보다 살짝 진하게)
  headerBg: '#F7F4FF',   // 헤더 배경(본문보다 밝은 라벤더)
}

/** 취미 카드용 밝은 그라데이션 — 취미 순번(index)으로 순환 배정. */
export const cardGradients = [
  'linear-gradient(135deg, #35D6A2 0%, #12B586 100%)', // green
  'linear-gradient(135deg, #A98BFA 0%, #7C5CF0 100%)', // purple
  'linear-gradient(135deg, #46BEF8 0%, #4F7BF7 100%)', // blue
  'linear-gradient(135deg, #FBBF3C 0%, #F59321 100%)', // amber
  'linear-gradient(135deg, #FB84AE 0%, #F45C8C 100%)', // pink
  'linear-gradient(135deg, #2FD3E6 0%, #08AEC9 100%)', // cyan
]

/** 사용자(gen) 영역 AntD 테마. 관리자와 다른 스타일(퍼플·큰 라운드)을 여기서 관리. */
export const genTheme: ThemeConfig = {
  token: {
    colorPrimary: gen.primary,
    colorLink: gen.primary,
    colorLinkHover: gen.accentText,
    borderRadius: 14,
    fontSize: 15,
  },
  components: {
    Card: { borderRadiusLG: 18 },
    Button: { controlHeight: 38, fontWeight: 600 },
    Tag: { borderRadiusSM: 10 },
  },
}
