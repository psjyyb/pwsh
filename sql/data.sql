-- =====================================================================
-- 기본(seed) 데이터
-- 대상: code(공통코드), config(환경설정 1행), auth_group(관리자 권한그룹)
-- 주의: 실행 시 PGCLIENTENCODING=UTF8 필수 (한글 깨짐 방지)
-- 관리자 계정(member) + 매핑(auth_member)은 앱 DataInitializer가
--   기동 시 자동 생성(비밀번호 BCrypt 암호화). 여기 넣지 않음.
-- 코드체계: 그룹(MEM00) + 하위코드(MEM01, MEM02 ...) 2단. 최상위 그룹의 부모는 'ROOT'.
-- audit: 시스템 초기데이터이므로 reg_id/upd_id='system', ip='127.0.0.1'
-- =====================================================================

-- ============================ 공통코드 (code) ============================
INSERT INTO code (code_id, p_code_id, name, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
-- ── 코드 그룹(부모) ──
('MEM00',      'ROOT', '회원유형',   1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('GEN00',      'ROOT', '성별',       2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('STATUS00',   'ROOT', '계정상태',   3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('POLICY00',   'ROOT', '약관유형',   5, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('BOARD00', 'ROOT', '게시판유형', 6, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MENU00',     'ROOT', '메뉴연결유형', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('EVENT00',    'ROOT', '이벤트유형',   8, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('RECRUIT00',  'ROOT', '모집상태',     9, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('APPLY00',    'ROOT', '신청상태',    10, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('HOBBYLV00',  'ROOT', '취미난이도',  11, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT00',   'ROOT', '신고사유',    12, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA00',     'ROOT', '지역(시도)',  13, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('ATTEND00',   'ROOT', '참석결과',    14, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 회원유형 (member.type_cd) ──
('MEM01', 'MEM00', '사용자', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MEM02', 'MEM00', '관리자', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 성별 (member.gender_cd) ──
('GEN01', 'GEN00', '남', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('GEN02', 'GEN00', '여', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 계정상태 (member.status_cd) ──
('STATUS01', 'STATUS00', '정상', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('STATUS02', 'STATUS00', '잠금', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('STATUS03', 'STATUS00', '정지', 3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 약관유형 (policy.type_cd) ──
('POLICY01', 'POLICY00', '회원가입약관',     1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('POLICY02', 'POLICY00', '개인정보처리방침', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('POLICY03', 'POLICY00', '이용약관',         3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 게시판유형 (board.type_cd) ──
('BOARD03', 'BOARD00', '1:1',    1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('BOARD02', 'BOARD00', 'FAQ',    2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('BOARD01', 'BOARD00', '일반',   3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('BOARD04', 'BOARD00', '갤러리', 4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 메뉴 연결유형 (menu.conn_cd) ──
('MENU01', 'MENU00', 'URL(화면)',  1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MENU02', 'MENU00', '게시판',      2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MENU03', 'MENU00', '페이지',      3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MENU04', 'MENU00', '그룹(폴더)',  4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 이벤트 유형 (event_log.event_cd) ──
('LOGIN',  'EVENT00', '로그인', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('INSERT', 'EVENT00', '등록',   2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('UPDATE', 'EVENT00', '수정',   3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('DELETE', 'EVENT00', '삭제',   4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
-- 관리자 조치(감사): 컨트롤러 메서드명이 insert/update/delete가 아니라 AOP가 못 잡는 지점 —
-- 각 서비스에서 명시적으로 기록한다. '누가 언제 무엇에 어떤 조치를 했는지'를 유형만으로 알 수 있게 세분화.
('REPORT_RESOLVE', 'EVENT00', '신고 삭제조치', 5, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT_DISMISS', 'EVENT00', '신고 반려',     6, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT_REOPEN',  'EVENT00', '신고 되돌리기', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MEMBER_SUSPEND',   'EVENT00', '회원 정지',     8, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MEMBER_RESTORE',   'EVENT00', '회원 정지해제', 9, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MEMBER_LOGOUT',    'EVENT00', '강제 로그아웃', 10, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MEMBER_AUTH_GROUP',   'EVENT00', '권한그룹 변경', 11, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 모집상태 (recruit.status_cd) ──
('RECRUIT01', 'RECRUIT00', '모집중', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('RECRUIT02', 'RECRUIT00', '마감',   2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 신청상태 (recruit_apply.apply_cd) ──
('APPLY01', 'APPLY00', '대기', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('APPLY02', 'APPLY00', '수락', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('APPLY03', 'APPLY00', '거절', 3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 지역: 시/도 (recruit.area_cd) — 모집 지역 필터 표준화. 상세 주소는 region(자유입력) ──
('AREA01', 'AREA00', '서울',    1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA02', 'AREA00', '부산',    2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA03', 'AREA00', '대구',    3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA04', 'AREA00', '인천',    4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA05', 'AREA00', '광주',    5, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA06', 'AREA00', '대전',    6, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA07', 'AREA00', '울산',    7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA08', 'AREA00', '세종',    8, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA09', 'AREA00', '경기',    9, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA10', 'AREA00', '강원',   10, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA11', 'AREA00', '충북',   11, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA12', 'AREA00', '충남',   12, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA13', 'AREA00', '전북',   13, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA14', 'AREA00', '전남',   14, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA15', 'AREA00', '경북',   15, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA16', 'AREA00', '경남',   16, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA17', 'AREA00', '제주',   17, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 참석 결과 (recruit_apply.attend_cd) — 모임 종료 후 주최자가 기록. 노쇼는 신뢰지표에 반영 ──
('ATTEND01', 'ATTEND00', '참석',        1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('ATTEND02', 'ATTEND00', '불참(통보)',  2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('ATTEND03', 'ATTEND00', '노쇼',        3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 신고 사유 (report.reason_cd) ──
('REPORT01', 'REPORT00', '스팸·광고',       1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT02', 'REPORT00', '욕설·비방',       2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT03', 'REPORT00', '음란·부적절',     3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT04', 'REPORT00', '허위·사기',       4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT05', 'REPORT00', '개인정보 노출',   5, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT06', 'REPORT00', '기타',            6, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 취미 난이도 (hobby.difficulty_cd) ──
('HOBBYLV01', 'HOBBYLV00', '입문', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('HOBBYLV02', 'HOBBYLV00', '초급', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('HOBBYLV03', 'HOBBYLV00', '중급', 3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('HOBBYLV04', 'HOBBYLV00', '고급', 4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ============================ 환경설정 (config, 단일 행) ============================
INSERT INTO config (fail_cnt_limit, fail_lock_mins, password_expire_days, session_expire_mins, del_log_days, acc_ip_yn, title, menu_version)
VALUES (5, 5, 90, 30, 365, 'N', '취만사', 1);

-- ============================ 관리자 권한그룹 (auth_group) ============================
INSERT INTO auth_group (auth_group_id, name, description, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES ('ADMIN', '관리자', '전체 메뉴/기능 권한', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ============================ 기본 관리자 메뉴 (menu, area=ADM, 2차 구조) ============================
-- 최상위 부모=0 (menu_id 0은 미사용 예약, 대시보드는 menu_id 14). menu_id 명시 후 시퀀스 보정.
INSERT INTO menu (menu_id, p_menu_id, area, name, sort_no, conn_cd, conn_id, link_url, target_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(14,  0, 'ADM', '대시보드',       1, 'MENU01', 0, '/adm/dashboard', 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 1,  0, 'ADM', '시스템관리',     2, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 2,  1, 'ADM', '공통코드관리',   1, 'MENU01', 0, '/adm/code',      'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 3,  1, 'ADM', '메뉴관리',       2, 'MENU01', 0, '/adm/menu',      'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 4,  1, 'ADM', '일반페이지관리', 3, 'MENU01', 0, '/adm/page',      'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 5,  1, 'ADM', '팝업관리',       4, 'MENU01', 0, '/adm/popup',     'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 6,  1, 'ADM', '약관관리',       5, 'MENU01', 0, '/adm/policy',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 7,  1, 'ADM', '환경설정',       6, 'MENU01', 0, '/adm/config',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 8,  0, 'ADM', '회원관리',       3, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 9,  8, 'ADM', '사용자관리',     1, 'MENU01', 0, '/adm/member',      'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(13,  8, 'ADM', '권한그룹관리',   2, 'MENU01', 0, '/adm/authgroup',   'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(10,  0, 'ADM', '로그관리',       4, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(12, 10, 'ADM', '활동로그',       1, 'MENU01', 0, '/adm/eventlog',  'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
-- 게시판 관리 그룹: 게시판 설정(관리화면 URL) + 게시판별 관리(연결유형=게시판 → /adm/post/{conn_id})
--   ※ 취미 게시판은 여기에 메뉴로 넣지 않는다 — 취미 등록 시 게시판이 자동 생성돼(HobbyService)
--     취미가 늘 때마다 메뉴를 손대야 하기 때문. 게시판 설정 목록의 '글 관리'로 진입한다.
(26,  0, 'ADM', '게시판 관리',    5, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(27, 26, 'ADM', '게시판 설정',    1, 'MENU01', 0, '/adm/board',   'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(28, 26, 'ADM', '공지사항',       2, 'MENU02', 1, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(29, 26, 'ADM', 'FAQ',            3, 'MENU02', 2, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(30, 26, 'ADM', '갤러리',         4, 'MENU02', 3, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(31, 26, 'ADM', '1:1문의',        5, 'MENU02', 4, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(46,  0, 'ADM', '취미 관리',      6, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(41, 46, 'ADM', '취미 정보 관리', 1, 'MENU01', 0, '/adm/hobby',     'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(45, 46, 'ADM', '모집 관리',      2, 'MENU01', 0, '/adm/recruit',   'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(43,  0, 'ADM', '커뮤니티 관리',  7, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(42, 43, 'ADM', '신고관리',       1, 'MENU01', 0, '/adm/report',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 사용자(GEN) 메뉴 — 취미 커뮤니티(도감 중심).
--  · 취미는 상단 메뉴가 아니라 메인(도감 카드) → 취미 허브(/gen/hobby/{id})로 진입 → 게시판/모집/레벨.
--    취미를 상단에 나열하지 않으므로 취미가 늘어도 메뉴가 깔끔.
--  · 모집 = URL(MENU01) /gen/recruit. 내 피드·마이페이지·1:1문의는 회원 전용(GUEST 권한 제외).
INSERT INTO menu (menu_id, p_menu_id, area, name, sort_no, conn_cd, conn_id, link_url, target_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(20,  0, 'GEN', '메인',       1, 'MENU01', 0, '/gen/main',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(47,  0, 'GEN', '내 피드',    2, 'MENU01', 0, '/gen/feed',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(37,  0, 'GEN', '모집',       3, 'MENU01', 0, '/gen/recruit', 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(21,  0, 'GEN', '공지사항',   4, 'MENU02', 1, NULL,           'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(22,  0, 'GEN', '고객센터',   5, 'MENU04', 0, NULL,           'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(23, 22, 'GEN', 'FAQ',        1, 'MENU02', 2, NULL,           'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(24, 22, 'GEN', '1:1문의',    2, 'MENU02', 4, NULL,           'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(25,  0, 'GEN', '마이페이지', 6, 'MENU01', 0, '/gen/mypage',  'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- menu_id를 명시 삽입했으므로 IDENTITY 시퀀스를 현재 최대값으로 보정
SELECT setval(pg_get_serial_sequence('menu', 'menu_id'), (SELECT MAX(menu_id) FROM menu));

-- 관리자 메뉴 기본 아이콘(사이드바 표시). 링크(link_url)별 매핑 — 프론트 MenuGlyph 키.
UPDATE menu SET icon = CASE
    WHEN link_url LIKE '%/user%'    THEN 'user'
    WHEN link_url LIKE '%/authgroup%' THEN 'group'
    WHEN link_url LIKE '%/menu%'    THEN 'list'
    WHEN link_url LIKE '%/code%'    THEN 'code'
    WHEN link_url LIKE '%/hobby%'   THEN 'grid'
    WHEN link_url LIKE '%/recruit%' THEN 'group'
    WHEN link_url LIKE '%/board%' OR link_url LIKE '%/post%' THEN 'board'
    WHEN link_url LIKE '%/page%'    THEN 'page'
    WHEN link_url LIKE '%/popup%'   THEN 'popup'
    WHEN link_url LIKE '%/policy%'  THEN 'policy'
    WHEN link_url LIKE '%/file%'    THEN 'file'
    WHEN link_url LIKE '%/eventlog%' OR link_url LIKE '%/log%' THEN 'log'
    WHEN link_url LIKE '%/config%'  THEN 'setting'
    WHEN link_url LIKE '%/report%'  THEN 'flag'
    WHEN link_url LIKE '%/dashboard%' THEN 'grid'
    ELSE icon END
WHERE area = 'ADM' AND link_url IS NOT NULL AND link_url <> '';

-- ============================ 권한 시드 (그룹/매핑/메뉴권한) ============================
-- 회원(로그인)·비회원(비로그인 공개) 그룹 (ADMIN은 위에서 등록됨)
INSERT INTO auth_group (auth_group_id, name, description, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('MEMBER', '회원',   '로그인 회원 권한',        'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('GUEST',  '비회원', '비로그인(공개) 접근 권한', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 사용자-그룹 매핑 (admin→ADMIN, user→MEMBER). 계정은 DataInitializer가 기동 시 생성.
INSERT INTO auth_member (member_id, auth_group_id, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('admin', 'ADMIN', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('user', 'MEMBER', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ADMIN: 관리자 전체 메뉴 권한(ADM)
INSERT INTO auth (menu_id, conn_id, type, menu_yn, search_yn, mod_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'ADMIN', 'GRP', 'Y', 'Y', 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM menu WHERE area = 'ADM';

-- MEMBER(회원): GEN 전체(공개 + 회원전용). 로그인 시 이 메뉴 구성이 노출됨.
INSERT INTO auth (menu_id, conn_id, type, menu_yn, search_yn, mod_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'MEMBER', 'GRP', 'Y', 'Y', 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM menu WHERE area = 'GEN';

-- GUEST(비회원): GEN 상단 공개 메뉴 = 메인(20)·모집(37)·공지사항(21)·고객센터(22)·FAQ(23).
--   ※ 취미 게시판·갤러리는 상단 메뉴에 두지 않고 '도감 중심'으로 접근(메인 카드→취미 허브→게시판).
--     공개 취미 게시판 열람 인가는 GenAccessGuard가 담당(메뉴 grant 불필요). 1:1문의(24)·마이페이지(25)는 회원 전용 제외.
-- 열람은 공개, 글쓰기·모집·신청 등 쓰기는 백엔드가 로그인 요구.
INSERT INTO auth (menu_id, conn_id, type, menu_yn, search_yn, mod_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'GUEST', 'GRP', 'Y', 'Y', 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM menu WHERE area = 'GEN' AND menu_id IN (20, 37, 21, 22, 23);

-- ============================ 기본 게시판 (board) ============================
-- 공용: id 1 공지(001)/2 FAQ(002)/3 갤러리(004)/4 1:1(003).
-- 취미 게시판(카테고리): id 5~26 = 일반형(001), 사진 첨부 가능. 취미 1개 = 게시판 1개.
--   취미를 추가할 땐 관리자 화면(취미 정보 관리)에서 등록하면 게시판이 자동 생성된다.
--   여기 시드는 기본 도감 15종에 대응한다(아래 hobby와 board_id로 짝을 맞춘다).
INSERT INTO board (board_id, name, type_cd, description, list_cnt,
    file_yn, file_cnt_limit, file_size_limit_mb, notice_yn, new_cnt, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES
    (1, '공지사항', 'BOARD01', '기본 공지 게시판',  10, 'Y', 5, 10, 'Y', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (2, 'FAQ',     'BOARD02', 'FAQ 게시판',        10, 'N', 5, 10, 'N', 0, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (3, '갤러리',  'BOARD04', '갤러리 게시판',      12, 'Y', 5, 10, 'N', 0, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (4, '1:1문의', 'BOARD03', '1:1 문의 게시판',    10, 'Y', 5, 10, 'N', 0, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (5, '등산', 'BOARD01', '등산 모임·정보 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (6, '보드게임', 'BOARD01', '보드게임 모임·정보 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (7, '낚시', 'BOARD01', '낚시 모임·정보 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (8, '러닝', 'BOARD01', '러닝 모임·정보 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (9, '크로스핏', 'BOARD01', '크로스핏 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (10, '클라이밍', 'BOARD01', '클라이밍 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (11, '요가', 'BOARD01', '요가 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (12, '캠핑', 'BOARD01', '캠핑 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (20, '사진', 'BOARD01', '사진 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (21, '베이킹', 'BOARD01', '베이킹 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (22, '기타', 'BOARD01', '기타 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (23, '자전거', 'BOARD01', '자전거 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (24, '서핑', 'BOARD01', '서핑 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (25, '도예', 'BOARD01', '도예 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (26, '배드민턴', 'BOARD01', '배드민턴 정보·모임 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('board', 'board_id'), (SELECT MAX(board_id) FROM board));

-- ============================ 기본 취미 카탈로그 (hobby) ============================
-- 취미(카테고리) = 게시판. 위 board와 board_id로 1:1 대응(등산1→5 … 배드민턴15→26).
-- ★ GenAccessGuard.isHobbyBoard가 이 매핑으로 취미 게시판의 공개 여부를 판정한다.
--   base 설치(data.sql만)에서도 취미 커뮤니티가 동작하도록 여기서 반드시 시드한다.
-- hobby_id 9~15는 결번(개발 중 삭제된 번호) — sort_no가 실제 노출 순서다.
INSERT INTO hobby (hobby_id, name, summary, intro, guide, difficulty_cd, equipment, estimated_cost, board_id, sort_no, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, '등산', '가까운 산부터 시작하는 건강한 취미',
 '<p>등산은 장비 부담이 적고 어디서나 시작할 수 있는 대표적인 야외 취미입니다. 체력 향상과 스트레스 해소에 좋습니다.</p>',
 '<p>1) 동네 뒷산·낮은 코스부터 시작하세요.</p><p>2) 편한 운동화 → 익숙해지면 등산화.</p><p>3) 물·간식·여벌옷을 챙기세요.</p>',
 'HOBBYLV01', '운동화(입문)/등산화, 배낭, 물통', '입문 5만원 내외', 5, 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, '보드게임', '실내에서 함께 즐기는 두뇌 놀이',
 '<p>보드게임은 남녀노소 함께 즐길 수 있는 실내 취미입니다. 카페에서 부담 없이 시작할 수 있어요.</p>',
 '<p>1) 보드게임 카페에서 다양한 게임을 경험해 보세요.</p><p>2) 입문용(스플렌더·카탄 등)부터.</p><p>3) 모임에 참여하면 룰을 쉽게 배웁니다.</p>',
 'HOBBYLV01', '없음(카페 이용) / 소장 시 게임 구매', '카페 2~3시간 1만원대', 6, 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, '낚시', '기다림의 여유를 즐기는 취미',
 '<p>낚시는 자연 속에서 여유를 즐기는 취미입니다. 민물·바다 등 종류가 다양합니다.</p>',
 '<p>1) 가까운 낚시터·좌대에서 시작.</p><p>2) 입문 세트(낚싯대+릴)로 충분.</p><p>3) 지역 물때·어종 정보를 확인하세요.</p>',
 'HOBBYLV02', '낚싯대, 릴, 채비, 아이스박스', '입문 세트 10만원 내외', 7, 3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(4, '러닝', '언제 어디서나 가볍게 시작하는 유산소 취미',
 '<p>러닝은 장비 부담이 거의 없고 어디서나 바로 시작할 수 있는 대표적인 유산소 운동입니다.</p>',
 '<p>1) 편한 러닝화부터 준비.</p><p>2) 걷기 → 걷뛰기 → 달리기 순으로.</p><p>3) 무리하지 말고 주 2~3회부터 시작.</p>',
 'HOBBYLV01', '러닝화, 편한 복장', '러닝화 5~10만원', 8, 4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(5, '크로스핏', '짧고 굵게 끝내는 고강도 순환운동',
 '<p>크로스핏은 역도·체조·유산소를 섞은 고강도 순환운동입니다. 매일 다른 운동(WOD)을 짧은 시간에 몰아서 합니다.</p><p>동작을 배우는 초반에는 무게보다 자세가 훨씬 중요합니다.</p>',
 '<p>1) 박스(체육관) 무료 체험이나 기초반(온보딩)으로 시작하세요.</p><p>2) 첫 달은 맨몸·빈 봉으로 자세만 익힙니다.</p><p>3) 부위별 통증은 참지 말고 코치에게 바로 알리세요.</p>',
 'HOBBYLV03', '운동화, 편한 복장(이후 보호대·역도화)', '센터 회비 월 10~15만원', 9, 5, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(6, '클라이밍', '실내 암장에서 시작하는 짜릿한 전신운동',
 '<p>클라이밍은 문제(루트)를 풀어가는 재미와 전신 운동 효과를 동시에 주는 실내 취미입니다.</p>',
 '<p>1) 가까운 실내 암장 원데이 클래스로 시작.</p><p>2) 암벽화·초크만 있으면 충분.</p><p>3) 볼더링(낮은 벽)부터 차근차근.</p>',
 'HOBBYLV02', '암벽화, 초크백', '원데이 2~3만원', 10, 6, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(7, '요가', '몸과 마음을 함께 돌보는 균형 운동',
 '<p>요가는 유연성과 코어 근력, 호흡·명상까지 아우르는 취미입니다. 남녀노소 부담 없이 시작할 수 있어요.</p>',
 '<p>1) 매트 하나면 집에서도 가능.</p><p>2) 기초 시퀀스부터 천천히.</p><p>3) 무리한 동작은 욕심내지 마세요.</p>',
 'HOBBYLV01', '요가매트, 편한 복장', '매트 2~4만원', 11, 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(8, '캠핑', '자연 속에서 쉬어가는 힐링 취미',
 '<p>캠핑은 자연에서 하루를 보내며 재충전하는 취미입니다. 장비는 차차 갖춰도 됩니다.</p>',
 '<p>1) 처음엔 오토캠핑장부터.</p><p>2) 렌탈로 장비 체험 후 구매.</p><p>3) 안전·화기 수칙을 꼭 지키세요.</p>',
 'HOBBYLV02', '텐트, 침낭, 랜턴, 의자', '입문 세트 20만원 내외', 12, 8, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(16, '사진', '일상을 남기는 가장 쉬운 취미',
 '<p>사진은 장비보다 <b>눈</b>이 먼저입니다. 스마트폰만 있어도 충분히 시작할 수 있고, 같은 장소를 다른 시간에 찍어보는 것만으로 실력이 늘어요.</p><p>인물·풍경·거리·음식 등 찍고 싶은 대상을 하나 정하면 방향이 훨씬 빨리 잡힙니다.</p>',
 '<h3>이렇게 시작하세요</h3><ol><li>스마트폰 카메라의 격자(3분할) 기능을 켜고 구도부터 연습합니다.</li><li>같은 피사체를 아침·낮·해질녘에 각각 찍어 빛의 차이를 익힙니다.</li><li>일주일에 30장만 골라 남기고 나머지는 지우는 연습을 합니다.</li><li>보정은 밝기·대비·채도 세 가지만 만지면서 시작하세요.</li></ol>',
 'HOBBYLV01', '스마트폰 또는 미러리스 카메라, 여분 배터리, SD카드', '스마트폰이면 0원 / 입문 미러리스 60~90만원', 20, 9, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(17, '베이킹', '오븐 하나로 시작하는 홈 디저트',
 '<p>베이킹은 <b>계량이 곧 실력</b>입니다. 눈대중을 줄이고 저울을 쓰는 것만으로 결과가 크게 달라집니다.</p><p>쿠키·머핀처럼 반죽이 단순한 것부터 시작하면 실패가 적습니다.</p>',
 '<h3>이렇게 시작하세요</h3><ol><li>전자저울로 g 단위 계량에 익숙해집니다.</li><li>첫 도전은 버터 쿠키나 머핀처럼 발효가 없는 레시피로.</li><li>오븐은 예열 10분을 반드시 지키고, 우리 집 오븐의 온도 편차를 메모해 둡니다.</li><li>같은 레시피를 세 번 반복해 감을 익힌 뒤 다음 레시피로 넘어가세요.</li></ol>',
 'HOBBYLV01', '오븐 또는 에어프라이어, 전자저울, 볼, 실리콘 주걱, 오븐팬', '초기 도구 10~15만원 / 재료 회당 5천~1만원', 21, 10, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(18, '기타', '코드 4개로 노래 한 곡',
 '<p>기타는 <b>C·G·Am·F</b> 네 개 코드만 잡을 수 있으면 부를 수 있는 노래가 수백 곡입니다.</p><p>손가락이 아픈 첫 2주만 넘기면 확실히 쉬워집니다.</p>',
 '<h3>이렇게 시작하세요</h3><ol><li>튜너 앱으로 조율하는 습관부터 만듭니다(매번 연주 전에).</li><li>하루 15분씩 코드 전환만 반복합니다. 소리보다 전환 속도가 먼저입니다.</li><li>F 코드는 처음엔 잡히지 않는 게 정상입니다. 카포를 써서 우회해도 됩니다.</li><li>좋아하는 노래 한 곡을 끝까지 완성하는 것을 첫 목표로 하세요.</li></ol>',
 'HOBBYLV02', '통기타, 피크, 카포, 튜너 앱, 여분 스트링', '입문 통기타 15~30만원', 22, 11, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(19, '자전거', '도심에서 바로 시작하는 라이딩',
 '<p>자전거는 <b>헬멧과 라이트</b>만 갖추면 오늘 바로 시작할 수 있습니다.</p><p>한강·천변 자전거도로처럼 차와 섞이지 않는 구간에서 시작하면 부담이 적습니다.</p>',
 '<h3>이렇게 시작하세요</h3><ol><li>안장 높이를 맞춥니다. 페달이 가장 아래일 때 무릎이 살짝 굽는 정도가 기준입니다.</li><li>첫 라이딩은 왕복 10km 이내, 평지 코스로.</li><li>공기압을 매주 확인하세요. 펑크의 절반은 공기압 부족에서 옵니다.</li><li>수신호(정지·좌우 회전)를 익히면 그룹 라이딩이 편해집니다.</li></ol>',
 'HOBBYLV01', '자전거, 헬멧, 전조등·후미등, 장갑, 펑크 패치', '생활차 20~40만원 / 로드 입문 70만원부터', 23, 12, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(20, '서핑', '파도 위에서 균형 잡기',
 '<p>서핑은 <b>패들링과 체력</b>이 8할입니다. 처음엔 파도를 타기보다 보드 위에 엎드려 나아가는 연습이 대부분입니다.</p><p>양양·제주 등 강습이 잘 갖춰진 곳에서 첫 입문을 권합니다.</p>',
 '<h3>이렇게 시작하세요</h3><ol><li>첫 두 번은 반드시 강습을 받으세요. 안전 수칙과 파도 읽기를 배웁니다.</li><li>육상에서 테이크오프(일어서기) 동작을 반복 연습합니다.</li><li>이안류·조류 정보를 확인하고, 사람이 있는 구역에서만 입수합니다.</li><li>파도가 작은 날이 오히려 연습에 좋습니다.</li></ol>',
 'HOBBYLV03', '보드(렌탈 가능), 슈트, 리쉬, 왁스, 래시가드', '강습 1회 6~10만원 / 장비 렌탈 3~5만원', 24, 13, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(21, '도예', '흙으로 그릇을 빚는 시간',
 '<p>도예는 결과보다 <b>손의 감각</b>을 다루는 취미입니다. 가마와 물레가 필요해 대부분 공방에서 시작합니다.</p><p>한 작품이 완성되기까지 건조·초벌·유약·재벌로 2~4주가 걸립니다.</p>',
 '<h3>이렇게 시작하세요</h3><ol><li>원데이 클래스로 물레와 핸드빌딩 중 어느 쪽이 맞는지 확인합니다.</li><li>첫 작품은 컵이나 접시처럼 형태가 단순한 것으로.</li><li>흙의 두께를 일정하게 유지하는 것이 갈라짐을 막는 핵심입니다.</li><li>완성까지 몇 주가 걸리니 여러 점을 동시에 만들어 두세요.</li></ol>',
 'HOBBYLV02', '공방 이용(물레·가마), 조각도, 앞치마', '원데이 4~7만원 / 정기 클래스 월 15~25만원', 25, 14, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(22, '배드민턴', '실내에서 즐기는 전신 운동',
 '<p>배드민턴은 <b>날씨와 무관</b>하게 할 수 있고, 둘만 있어도 성립합니다.</p><p>생각보다 심장이 많이 뛰는 운동이라 준비운동을 빼먹으면 다치기 쉽습니다.</p>',
 '<h3>이렇게 시작하세요</h3><ol><li>라켓은 가볍고(85g 내외) 유연한 입문용으로 고릅니다.</li><li>손목이 아니라 팔 전체로 스윙하는 하이클리어부터 배웁니다.</li><li>실내 코트용 논마킹 운동화를 신어야 미끄러지지 않습니다.</li><li>동네 배드민턴 클럽은 대체로 초보를 환영합니다. 혼자보다 훨씬 빠르게 늡니다.</li></ol>',
 'HOBBYLV01', '라켓, 셔틀콕, 실내용 운동화, 그립테이프', '라켓 5~15만원 / 체육관 대여 시간당 5천~1만원', 26, 15, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('hobby', 'hobby_id'), (SELECT MAX(hobby_id) FROM hobby));

-- ============================ 이벤트 로그 샘플 (event_log) ============================
-- 실제 운영 시 EventLogAspect/로그인 핸들러가 자동 적재. 아래는 화면 확인용 예시(기기/UA 포함).
INSERT INTO event_log (event_cd, member_id, target_table, target_id, device_type, user_agent, reg_dt, reg_ip) VALUES
('LOGIN',  'admin', NULL,       NULL,  'desktop', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36', NOW(), '127.0.0.1'),
('INSERT', 'admin', 'popup',  '1',   'desktop', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36', NOW(), '127.0.0.1'),
('UPDATE', 'admin', 'policy', '2',   'mobile',  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1', NOW(), '127.0.0.1'),
('DELETE', 'admin', 'menu',   '25',  'desktop', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36', NOW(), '127.0.0.1');
