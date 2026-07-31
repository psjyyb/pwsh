import type { ThemeConfig } from 'antd'

/** 관리자(adm) 영역 AntD 테마. 색상·라운드·사이드바 메뉴(글자/높이/아이콘) 기준. */
export const admTheme: ThemeConfig = {
  token: {
    colorPrimary: '#1677ff',
    borderRadius: 6,
  },
  components: {
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
