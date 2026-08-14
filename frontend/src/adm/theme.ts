import type { ThemeConfig } from 'antd'

/**
 * 관리자(adm) 영역 AntD 테마. 색상·라운드·사이드바 메뉴(글자/높이/아이콘) 기준.
 * 폰트는 최상위 ConfigProvider(main.tsx)에서 지정한 값을 상속한다(AntD는 중첩 시 토큰을 병합).
 *
 * 사용자(gen) 영역과 달리 장식을 넣지 않는다 — 관리 화면은 읽고 조작하는 도구라
 * 한 화면에 얼마나 담기는지(밀도)와 값을 얼마나 빨리 훑는지가 미관보다 중요하다.
 */
export const admTheme: ThemeConfig = {
  token: {
    colorPrimary: '#1677ff',
    borderRadius: 6,
  },
  components: {
    // 표가 주력 화면 — 행 높이를 줄여 한 화면에 더 담고, 헤더/호버는 값에 시선이 가도록 옅게
    Table: {
      cellPaddingBlock: 10,
      cellPaddingBlockSM: 7,
      headerBg: '#f6f8fa',
      headerColor: '#5b6472',
      rowHoverBg: '#f2f7ff',
      borderColor: '#e8eaed',
      headerSplitColor: '#e8eaed',
    },
    // 목록 위 검색/버튼 줄이 표와 붙지 않게(밀도를 높이면서도 구획은 유지)
    Card: { paddingLG: 18 },
    // 사이드바 메뉴: 글자 키우고 항목 높이·아이콘 크게, 선택 항목 강조
    Menu: {
      fontSize: 15,
      itemHeight: 46,
      iconSize: 18,
      itemMarginInline: 8,
      itemBorderRadius: 6,
      itemSelectedBg: '#e6f0ff',
      itemSelectedColor: '#1677ff',
      subMenuItemBg: 'transparent',
    },
    // 상단 탭(카드형): 활성=흰 배경/파란 글씨, 비활성=진한 회색 배경/흐린 글씨 → 회색 레이아웃 위에서도 구분
    Tabs: {
      cardBg: '#e4e7eb',
      cardGutter: 4,
      itemColor: '#7c828a',
      itemSelectedColor: '#1677ff',
      itemActiveColor: '#1677ff',
      itemHoverColor: '#1677ff',
    },
  },
}
