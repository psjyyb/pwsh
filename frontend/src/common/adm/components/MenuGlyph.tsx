import type { ReactNode } from 'react'

/**
 * 메뉴 아이콘 레지스트리 — menu.icon 키 → 라인 아이콘(SVG). 브랜드 무관 자체 제작 도형.
 * 관리자 메뉴관리에서 키를 선택해 저장하면 사이드바에 표시된다. 미지정/미등록 키는 'grid' 기본.
 */
const PATHS: Record<string, ReactNode> = {
  grid: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </>
  ),
  home: (
    <>
      <path d="M4 11l8-7 8 7" />
      <path d="M6 10v9h12v-9" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20c0-4 4-6 8-6s8 2 8 6" />
    </>
  ),
  group: (
    <>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3 19c0-3.3 3-5 6-5s6 1.7 6 5" />
      <path d="M16 5.5a3.2 3.2 0 0 1 0 6.4" />
      <path d="M17.5 14.2c2 .6 3.5 2 3.5 4.8" />
    </>
  ),
  list: (
    <>
      <line x1="4" y1="7" x2="20" y2="7" />
      <line x1="4" y1="12" x2="20" y2="12" />
      <line x1="4" y1="17" x2="20" y2="17" />
    </>
  ),
  code: (
    <>
      <polyline points="9 8 5 12 9 16" />
      <polyline points="15 8 19 12 15 16" />
    </>
  ),
  board: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <line x1="8" y1="9" x2="16" y2="9" />
      <line x1="8" y1="13" x2="14" y2="13" />
    </>
  ),
  page: (
    <>
      <path d="M6 3h8l4 4v14H6z" />
      <polyline points="14 3 14 7 18 7" />
    </>
  ),
  popup: (
    <>
      <rect x="4" y="5" width="16" height="14" rx="2" />
      <line x1="4" y1="9" x2="20" y2="9" />
    </>
  ),
  policy: (
    <>
      <path d="M6 3h9l3 3v15H6z" />
      <polyline points="9 14 11 16 15 12" />
    </>
  ),
  file: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />,
  log: (
    <>
      <circle cx="12" cy="12" r="8" />
      <polyline points="12 8 12 12 15 14" />
    </>
  ),
  setting: (
    <>
      <line x1="4" y1="8" x2="20" y2="8" />
      <circle cx="9" cy="8" r="2" />
      <line x1="4" y1="16" x2="20" y2="16" />
      <circle cx="15" cy="16" r="2" />
    </>
  ),
  chart: (
    <>
      <path d="M4 20h16" />
      <rect x="6" y="11" width="3" height="6" rx="0.5" />
      <rect x="11" y="7" width="3" height="10" rx="0.5" />
      <rect x="16" y="13" width="3" height="4" rx="0.5" />
    </>
  ),
  bell: (
    <>
      <path d="M6 9a6 6 0 0 1 12 0c0 4 2 5 2 5H4s2-1 2-5" />
      <path d="M10 19a2 2 0 0 0 4 0" />
    </>
  ),
  mail: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M3 7l9 6 9-6" />
    </>
  ),
  calendar: (
    <>
      <rect x="4" y="5" width="16" height="16" rx="2" />
      <line x1="4" y1="9" x2="20" y2="9" />
      <line x1="9" y1="3" x2="9" y2="6" />
      <line x1="15" y1="3" x2="15" y2="6" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6" />
      <line x1="20" y1="20" x2="15.5" y2="15.5" />
    </>
  ),
  lock: (
    <>
      <rect x="5" y="11" width="14" height="9" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </>
  ),
  key: (
    <>
      <circle cx="7.5" cy="7.5" r="3.5" />
      <path d="M10 10l9 9" />
      <path d="M15 14l3-3" />
    </>
  ),
  shield: <path d="M12 3l8 3v6c0 5-4 8-8 9-4-1-8-4-8-9V6z" />,
  star: <path d="M12 3l2.9 6 6.1.9-4.5 4.3 1.1 6.1L12 17.8 6.4 20.3l1.1-6.1L3 9.9 9.1 9z" />,
  bookmark: <path d="M7 3h10v18l-5-4-5 4z" />,
  tag: (
    <>
      <path d="M11 4H5a1 1 0 0 0-1 1v6l9 9 7-7-9-9z" />
      <circle cx="8" cy="8" r="1.2" />
    </>
  ),
  image: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <circle cx="9" cy="9" r="2" />
      <path d="M4 17l5-5 4 4 3-3 4 4" />
    </>
  ),
  download: (
    <>
      <path d="M12 4v10" />
      <path d="M8 11l4 4 4-4" />
      <path d="M5 20h14" />
    </>
  ),
  upload: (
    <>
      <path d="M12 20V9" />
      <path d="M8 12l4-4 4 4" />
      <path d="M5 5h14" />
    </>
  ),
  link: (
    <>
      <path d="M9.5 14.5l5-5" />
      <path d="M11 6l1-1a4 4 0 0 1 6 6l-1 1" />
      <path d="M13 18l-1 1a4 4 0 0 1-6-6l1-1" />
    </>
  ),
  edit: (
    <>
      <path d="M4 20h4L18 10l-4-4L4 16z" />
      <path d="M13 7l4 4" />
    </>
  ),
  trash: (
    <>
      <path d="M4 7h16" />
      <path d="M6 7l1 13h10l1-13" />
      <path d="M9 7V4h6v3" />
    </>
  ),
  check: <path d="M20 6L9 17l-5-5" />,
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <line x1="12" y1="11" x2="12" y2="16" />
      <line x1="12" y1="8" x2="12" y2="8" />
    </>
  ),
  help: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-1 .6-1 1-1 1.7" />
      <line x1="12" y1="17" x2="12" y2="17" />
    </>
  ),
  database: (
    <>
      <ellipse cx="12" cy="6" rx="7" ry="3" />
      <path d="M5 6v12c0 1.7 3.1 3 7 3s7-1.3 7-3V6" />
      <path d="M5 12c0 1.7 3.1 3 7 3s7-1.3 7-3" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="9" />
      <line x1="3" y1="12" x2="21" y2="12" />
      <path d="M12 3c3 3 3 15 0 18" />
      <path d="M12 3c-3 3-3 15 0 18" />
    </>
  ),
  phone: <path d="M6 3h3l2 5-2 1a12 12 0 0 0 5 5l1-2 5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 4 5a2 2 0 0 1 2-2z" />,
  chat: <path d="M20 4H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h4v4l5-4h7a1 1 0 0 0 1-1V5a1 1 0 0 0-1-1z" />,
  eye: (
    <>
      <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7z" />
      <circle cx="12" cy="12" r="3" />
    </>
  ),
  clipboard: (
    <>
      <rect x="6" y="4" width="12" height="17" rx="2" />
      <rect x="9" y="2" width="6" height="4" rx="1" />
    </>
  ),
  won: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M8 9l2 5 2-4 2 4 2-5" />
      <line x1="7.5" y1="12" x2="16.5" y2="12" />
      <line x1="7.5" y1="14" x2="16.5" y2="14" />
    </>
  ),
  filter: <path d="M4 5h16l-6 8v6l-4-2v-4z" />,
  box: (
    <>
      <path d="M12 3l8 4v10l-8 4-8-4V7z" />
      <path d="M4 7l8 4 8-4" />
      <path d="M12 11v10" />
    </>
  ),
  flag: (
    <>
      <path d="M5 3v18" />
      <path d="M5 4h11l-2 3 2 3H5" />
    </>
  ),
  location: (
    <>
      <path d="M12 21c5-5 7-8 7-11a7 7 0 0 0-14 0c0 3 2 6 7 11z" />
      <circle cx="12" cy="10" r="2.5" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),

  // ── 파일·문서 ──

  book: (
    <>
      <path d="M5 5a2 2 0 0 1 2-2h12v18H7a2 2 0 0 1-2-2z" />
      <line x1="9" y1="3" x2="9" y2="21" />
    </>
  ),
  printer: (
    <>
      <polyline points="7 8 7 3 17 3 17 8" />
      <rect x="4" y="8" width="16" height="7" rx="2" />
      <rect x="7" y="14" width="10" height="7" />
    </>
  ),

  // ── 보안·상태 ──

  warning: (
    <>
      <path d="M12 4l9 16H3z" />
      <line x1="12" y1="10" x2="12" y2="14" />
      <circle cx="12" cy="17" r="0.6" />
    </>
  ),

  close: (
    <>
      <line x1="6" y1="6" x2="18" y2="18" />
      <line x1="18" y1="6" x2="6" y2="18" />
    </>
  ),
  plus: (
    <>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </>
  ),
  minus: <line x1="5" y1="12" x2="19" y2="12" />,
  refresh: (
    <>
      <path d="M4 12a8 8 0 0 1 13.7-5.7L20 8" />
      <polyline points="20 4 20 8 16 8" />
      <path d="M20 12a8 8 0 0 1-13.7 5.7L4 16" />
      <polyline points="4 20 4 16 8 16" />
    </>
  ),

  sliders: (
    <>
      <line x1="4" y1="8" x2="20" y2="8" />
      <circle cx="10" cy="8" r="2" />
      <line x1="4" y1="16" x2="20" y2="16" />
      <circle cx="15" cy="16" r="2" />
    </>
  ),

  // ── 소통 ──


  send: (
    <>
      <path d="M21 4L3 11l7 3 3 7z" />
      <line x1="21" y1="4" x2="10" y2="14" />
    </>
  ),
  share: (
    <>
      <circle cx="6" cy="12" r="2.5" />
      <circle cx="17" cy="6" r="2.5" />
      <circle cx="17" cy="18" r="2.5" />
      <line x1="8.2" y1="10.8" x2="14.8" y2="7.2" />
      <line x1="8.2" y1="13.2" x2="14.8" y2="16.8" />
    </>
  ),

  // ── 사람·평가 ──

  heart: <path d="M12 20s-7-4.5-7-9a4 4 0 0 1 7-2.6A4 4 0 0 1 19 11c0 4.5-7 9-7 9z" />,

  award: (
    <>
      <circle cx="12" cy="9" r="5" />
      <path d="M9 13.5L8 21l4-2 4 2-1-7.5" />
    </>
  ),
  school: (
    <>
      <path d="M12 5l9 4-9 4-9-4z" />
      <path d="M7 12v4c0 1.5 2.5 3 5 3s5-1.5 5-3v-4" />
    </>
  ),

  // ── 미디어 ──
  video: (
    <>
      <rect x="3" y="6" width="12" height="12" rx="2" />
      <path d="M15 10l6-3v10l-6-3z" />
    </>
  ),
  camera: (
    <>
      <path d="M3 8h4l2-2h6l2 2h4v11H3z" />
      <circle cx="12" cy="13" r="3.5" />
    </>
  ),
  music: (
    <>
      <path d="M9 18V6l10-2v12" />
      <circle cx="7" cy="18" r="2" />
      <circle cx="17" cy="16" r="2" />
    </>
  ),

  // ── 상거래 ──
  cart: (
    <>
      <circle cx="9" cy="20" r="1.5" />
      <circle cx="17" cy="20" r="1.5" />
      <path d="M3 4h2l2.5 11h10L20 7H6" />
    </>
  ),
  card: (
    <>
      <rect x="3" y="6" width="18" height="12" rx="2" />
      <line x1="3" y1="10" x2="21" y2="10" />
    </>
  ),
  wallet: (
    <>
      <rect x="3" y="6" width="18" height="12" rx="2" />
      <line x1="15" y1="12" x2="19" y2="12" />
    </>
  ),
  ticket: <path d="M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4z" />,
  gift: (
    <>
      <rect x="3" y="9" width="18" height="11" rx="1" />
      <line x1="12" y1="9" x2="12" y2="20" />
      <path d="M12 9c-2-4-7-4-6-1 .5 1.5 4 1 6 1z" />
      <path d="M12 9c2-4 7-4 6-1-.5 1.5-4 1-6 1z" />
    </>
  ),
  truck: (
    <>
      <rect x="2" y="7" width="12" height="9" rx="1" />
      <path d="M14 10h4l3 3v3h-7z" />
      <circle cx="6" cy="18" r="1.8" />
      <circle cx="18" cy="18" r="1.8" />
    </>
  ),

  // ── 장소·조직 ──
  building: (
    <>
      <rect x="5" y="3" width="14" height="18" rx="1" />
      <line x1="9" y1="7" x2="9" y2="9" />
      <line x1="15" y1="7" x2="15" y2="9" />
      <line x1="9" y1="12" x2="9" y2="14" />
      <line x1="15" y1="12" x2="15" y2="14" />
      <path d="M10 21v-4h4v4" />
    </>
  ),
  store: (
    <>
      <path d="M4 9h16v11H4z" />
      <path d="M3 9l2-5h14l2 5" />
    </>
  ),
  map: (
    <>
      <path d="M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z" />
      <line x1="9" y1="4" x2="9" y2="18" />
      <line x1="15" y1="6" x2="15" y2="20" />
    </>
  ),

  // ── 통계 ──
  trend: (
    <>
      <polyline points="4 16 9 11 13 15 20 8" />
      <polyline points="15 8 20 8 20 13" />
    </>
  ),
  pie: (
    <>
      <path d="M12 3a9 9 0 1 0 9 9h-9z" />
      <path d="M14 3.5A9 9 0 0 1 20.5 10H14z" />
    </>
  ),
}

/** 선택 UI(메뉴관리 아이콘 피커)용 아이콘 키 목록 */
export const MENU_ICON_KEYS = Object.keys(PATHS)

/**
 * 아이콘 검색용 한글 별칭 — 키는 영문이라 한글로 찾는 운영자가 원하는 아이콘에 닿지 못한다.
 * 피커의 검색창이 키 + 이 별칭을 함께 본다. 별칭이 없는 키는 영문 키로만 검색된다.
 */
export const MENU_ICON_ALIASES: Record<string, string> = {
  grid: '격자 대시보드 메인',
  home: '홈 메인',
  user: '사용자 회원 사람 계정',
  group: '그룹 권한 조직 팀',
  list: '목록 리스트 메뉴',
  code: '코드 공통코드 개발',
  board: '게시판 글',
  page: '페이지 문서',
  popup: '팝업 레이어',
  policy: '약관 정책 동의',
  log: '로그 이력 기록',
  setting: '설정 환경설정 톱니',
  chart: '차트 통계 그래프 막대',
  bell: '알림 종 공지',
  mail: '메일 편지 이메일 문의',
  calendar: '달력 일정 캘린더',
  search: '검색 돋보기',
  lock: '잠금 자물쇠 보안 비밀',
  key: '열쇠 키 인증 권한',
  tag: '태그 라벨 분류 금칙어',
  image: '이미지 사진 그림',
  download: '다운로드 내려받기',
  upload: '업로드 올리기',
  link: '링크 연결 주소',
  edit: '수정 편집 연필',
  trash: '삭제 휴지통 버리기',
  info: '정보 안내',
  help: '도움말 물음표 FAQ 고객센터',
  database: '데이터베이스 DB 저장소',
  globe: '지구 웹 사이트 다국어',
  eye: '보기 조회 열람',
  clipboard: '클립보드 양식 신청',
  won: '원 금액 결제 정산',
  box: '상자 패키지 보관',
  flag: '깃발 신고 표시',
  location: '위치 지도 주소 핀',
  clock: '시계 시간 세션 접속',
  file: '파일 첨부 문서 자료 폴더',
  book: '책 매뉴얼 안내서 교육',
  printer: '프린터 인쇄 출력',
  shield: '방패 보안 차단 접속IP 보호',
  warning: '경고 주의 오류',
  check: '체크 확인 완료 승인',
  close: '닫기 취소 엑스 반려',
  plus: '추가 등록 더하기',
  minus: '제거 빼기',
  refresh: '새로고침 갱신 동기화',
  filter: '필터 조건 거르기',
  sliders: '조절 옵션 세부설정',
  chat: '대화 채팅 댓글 말풍선',
  phone: '전화 연락처 통화',
  send: '보내기 전송 발송',
  share: '공유 공유하기 확산',
  star: '별 즐겨찾기 추천 평점',
  heart: '하트 좋아요 관심',
  bookmark: '북마크 저장 스크랩',
  award: '상 훈장 인증 배지',
  school: '학교 교육 수료 학사',
  video: '영상 동영상 비디오',
  camera: '카메라 촬영 사진',
  music: '음악 소리 오디오',
  cart: '장바구니 쇼핑 주문',
  card: '카드 결제 신용카드',
  wallet: '지갑 잔액 포인트',
  ticket: '티켓 쿠폰 입장권 예약',
  gift: '선물 이벤트 경품',
  truck: '배송 택배 운송',
  building: '건물 기관 회사 지점',
  store: '상점 매장 가게',
  map: '지도 위치 안내도',
  trend: '추이 증가 성장 그래프',
  pie: '원형 차트 비율 통계',
}

export default function MenuGlyph({ name, size = 18 }: { name?: string; size?: number }) {
  const paths = (name && PATHS[name]) || PATHS.grid
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      style={{ display: 'block' }}
    >
      {paths}
    </svg>
  )
}
