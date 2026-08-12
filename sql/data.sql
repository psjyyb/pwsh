-- =====================================================================
-- 기본(seed) 데이터
-- 대상: t_code(공통코드), t_config(환경설정 1행), t_auth_grp(관리자 권한그룹)
-- 주의: 실행 시 PGCLIENTENCODING=UTF8 필수 (한글 깨짐 방지)
-- 관리자 계정(t_user) + 매핑(t_auth_user)은 앱 DataInitializer가
--   기동 시 자동 생성(비밀번호 BCrypt 암호화). 여기 넣지 않음.
-- 코드체계: 표준 CMS 기준(MEM00/MEM01 ...). 최상위 그룹의 부모는 'ROOT'.
-- audit: 시스템 초기데이터이므로 reg_id/upd_id='system', ip='127.0.0.1'
-- =====================================================================

-- ============================ 공통코드 (t_code) ============================
INSERT INTO t_code (code_id, p_code_id, code_nm, ordr, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
-- ── 코드 그룹(부모) ──
('MEM00',      'ROOT', '회원유형',   1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('GEN00',      'ROOT', '성별',       2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('STATUS00',   'ROOT', '계정상태',   3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('POLICY00',   'ROOT', '약관유형',   5, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('BBSINFO000', 'ROOT', '게시판유형', 6, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MENU00',     'ROOT', '메뉴연결유형', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('EVENT00',    'ROOT', '이벤트유형',   8, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('RECRUIT00',  'ROOT', '모집상태',     9, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('APPLY00',    'ROOT', '신청상태',    10, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('HOBBYLV00',  'ROOT', '취미난이도',  11, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT00',   'ROOT', '신고사유',    12, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('AREA00',     'ROOT', '지역(시도)',  13, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('ATTEND00',   'ROOT', '참석결과',    14, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 회원유형 (t_user.mem_cd) ──
('MEM01', 'MEM00', '사용자', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MEM02', 'MEM00', '관리자', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 성별 (t_user.gen_cd) ──
('GEN01', 'GEN00', '남', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('GEN02', 'GEN00', '여', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 계정상태 (t_user.status_cd) ──
('STATUS01', 'STATUS00', '정상', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('STATUS02', 'STATUS00', '잠금', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('STATUS03', 'STATUS00', '정지', 3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 약관유형 (t_policy.type_cd) ──
('POLICY01', 'POLICY00', '회원가입약관',     1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('POLICY02', 'POLICY00', '개인정보처리방침', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('POLICY03', 'POLICY00', '이용약관',         3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 게시판유형 (t_bbsinfo.bbsinfo_cd) ──
('BBSINFO003', 'BBSINFO000', '1:1',    1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('BBSINFO002', 'BBSINFO000', 'FAQ',    2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('BBSINFO001', 'BBSINFO000', '일반',   3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('BBSINFO004', 'BBSINFO000', '갤러리', 4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 메뉴 연결유형 (t_menu.conn_ty) ──
('MENU01', 'MENU00', 'URL(화면)',  1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MENU02', 'MENU00', '게시판',      2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MENU03', 'MENU00', '페이지',      3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MENU04', 'MENU00', '그룹(폴더)',  4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 이벤트 유형 (t_event_log.event_type) ──
('LOGIN',  'EVENT00', '로그인', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('INSERT', 'EVENT00', '등록',   2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('UPDATE', 'EVENT00', '수정',   3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('DELETE', 'EVENT00', '삭제',   4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 모집상태 (t_recruit.status_cd) ──
('RECRUIT01', 'RECRUIT00', '모집중', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('RECRUIT02', 'RECRUIT00', '마감',   2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 신청상태 (t_recruit_apply.apply_status) ──
('APPLY01', 'APPLY00', '대기', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('APPLY02', 'APPLY00', '수락', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('APPLY03', 'APPLY00', '거절', 3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 지역: 시/도 (t_recruit.area_cd) — 모집 지역 필터 표준화. 상세 주소는 region(자유입력) ──
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

-- ── 참석 결과 (t_recruit_apply.attend_cd) — 모임 종료 후 주최자가 기록. 노쇼는 신뢰지표에 반영 ──
('ATTEND01', 'ATTEND00', '참석',        1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('ATTEND02', 'ATTEND00', '불참(통보)',  2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('ATTEND03', 'ATTEND00', '노쇼',        3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 신고 사유 (t_report.reason_cd) ──
('REPORT01', 'REPORT00', '스팸·광고',       1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT02', 'REPORT00', '욕설·비방',       2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT03', 'REPORT00', '음란·부적절',     3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT04', 'REPORT00', '허위·사기',       4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT05', 'REPORT00', '개인정보 노출',   5, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('REPORT06', 'REPORT00', '기타',            6, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),

-- ── 취미 난이도 (t_hobby.difficulty_cd) ──
('HOBBYLV01', 'HOBBYLV00', '입문', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('HOBBYLV02', 'HOBBYLV00', '초급', 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('HOBBYLV03', 'HOBBYLV00', '중급', 3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('HOBBYLV04', 'HOBBYLV00', '고급', 4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ============================ 환경설정 (t_config, 단일 행) ============================
INSERT INTO t_config (fail_cnt_limit, fail_cnt_denied_ti, pw_expire_cnt, session_expire_cnt, del_log_cnt, acc_ip_yn, title, menu_version)
VALUES (5, 5, 90, 30, 365, 'N', '취만사', 1);

-- ============================ 관리자 권한그룹 (t_auth_grp) ============================
INSERT INTO t_auth_grp (authgrp_id, authgrp_nm, authgrp_desc, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES ('ADMIN', '관리자', '전체 메뉴/기능 권한', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ============================ 기본 관리자 메뉴 (t_menu, area=ADM, 2차 구조) ============================
-- 최상위 부모=0 (menu_id 0은 미사용 예약, 대시보드는 menu_id 14). menu_id 명시 후 시퀀스 보정.
INSERT INTO t_menu (menu_id, p_menu_id, area, menu_nm, ordr, conn_ty, conn_id, link_url, target_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(14,  0, 'ADM', '대시보드',       1, 'MENU01', 0, '/adm/dashboard', 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 1,  0, 'ADM', '시스템관리',     2, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 2,  1, 'ADM', '공통코드관리',   1, 'MENU01', 0, '/adm/code',      'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 3,  1, 'ADM', '메뉴관리',       2, 'MENU01', 0, '/adm/menu',      'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 4,  1, 'ADM', '일반페이지관리', 3, 'MENU01', 0, '/adm/page',      'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 5,  1, 'ADM', '팝업관리',       4, 'MENU01', 0, '/adm/popup',     'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 6,  1, 'ADM', '약관관리',       5, 'MENU01', 0, '/adm/policy',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 7,  1, 'ADM', '환경설정',       6, 'MENU01', 0, '/adm/config',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 8,  0, 'ADM', '회원관리',       3, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
( 9,  8, 'ADM', '사용자관리',     1, 'MENU01', 0, '/adm/user',      'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(13,  8, 'ADM', '권한그룹관리',   2, 'MENU01', 0, '/adm/authgrp',   'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(10,  0, 'ADM', '로그관리',       4, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(12, 10, 'ADM', '활동로그',       1, 'MENU01', 0, '/adm/eventlog',  'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
-- 게시판 관리 그룹: 게시판 설정(관리화면 URL) + 게시판별 관리(연결유형=게시판 → /adm/bbs/{conn_id})
(26,  0, 'ADM', '게시판 관리',    5, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(27, 26, 'ADM', '게시판 설정',    1, 'MENU01', 0, '/adm/bbsinfo',   'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(28, 26, 'ADM', '공지사항',       2, 'MENU02', 1, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(29, 26, 'ADM', 'FAQ',            3, 'MENU02', 2, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(30, 26, 'ADM', '갤러리',         4, 'MENU02', 3, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(31, 26, 'ADM', '1:1문의',        5, 'MENU02', 4, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(38, 26, 'ADM', '등산',           6, 'MENU02', 5, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(39, 26, 'ADM', '보드게임',       7, 'MENU02', 6, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(40, 26, 'ADM', '낚시',           8, 'MENU02', 7, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(46,  0, 'ADM', '취미 관리',      6, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(41, 46, 'ADM', '취미 정보 관리', 1, 'MENU01', 0, '/adm/hobby',     'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(45, 46, 'ADM', '모집 관리',      2, 'MENU01', 0, '/adm/recruit',   'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(43,  0, 'ADM', '커뮤니티 관리',  7, 'MENU04', 0, NULL,             'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(42, 43, 'ADM', '신고관리',       1, 'MENU01', 0, '/adm/report',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 사용자(GEN) 메뉴 — 취미 커뮤니티(도감 중심).
--  · 취미는 상단 메뉴가 아니라 메인(도감 카드) → 취미 허브(/gen/hobby/{id})로 진입 → 게시판/모집/레벨.
--    취미를 상단에 나열하지 않으므로 취미가 늘어도 메뉴가 깔끔.
--  · 모집 = URL(MENU01) /gen/recruit. 내 피드·마이페이지·1:1문의는 회원 전용(GUEST 권한 제외).
INSERT INTO t_menu (menu_id, p_menu_id, area, menu_nm, ordr, conn_ty, conn_id, link_url, target_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(20,  0, 'GEN', '메인',       1, 'MENU01', 0, '/gen/main',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(47,  0, 'GEN', '내 피드',    2, 'MENU01', 0, '/gen/feed',    'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(37,  0, 'GEN', '모집',       3, 'MENU01', 0, '/gen/recruit', 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(21,  0, 'GEN', '공지사항',   4, 'MENU02', 1, NULL,           'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(22,  0, 'GEN', '고객센터',   5, 'MENU04', 0, NULL,           'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(23, 22, 'GEN', 'FAQ',        1, 'MENU02', 2, NULL,           'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(24, 22, 'GEN', '1:1문의',    2, 'MENU02', 4, NULL,           'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(25,  0, 'GEN', '마이페이지', 6, 'MENU01', 0, '/gen/mypage',  'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- menu_id를 명시 삽입했으므로 IDENTITY 시퀀스를 현재 최대값으로 보정
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) FROM t_menu));

-- 관리자 메뉴 기본 아이콘(사이드바 표시). 링크(link_url)별 매핑 — 프론트 MenuGlyph 키.
UPDATE t_menu SET icon = CASE
    WHEN link_url LIKE '%/user%'    THEN 'user'
    WHEN link_url LIKE '%/authgrp%' THEN 'group'
    WHEN link_url LIKE '%/menu%'    THEN 'list'
    WHEN link_url LIKE '%/code%'    THEN 'code'
    WHEN link_url LIKE '%/hobby%'   THEN 'grid'
    WHEN link_url LIKE '%/recruit%' THEN 'group'
    WHEN link_url LIKE '%/bbsinfo%' OR link_url LIKE '%/bbs%' THEN 'board'
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
INSERT INTO t_auth_grp (authgrp_id, authgrp_nm, authgrp_desc, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('MEMBER', '회원',   '로그인 회원 권한',        'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('GUEST',  '비회원', '비로그인(공개) 접근 권한', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 사용자-그룹 매핑 (admin→ADMIN, user→MEMBER). 계정은 DataInitializer가 기동 시 생성.
INSERT INTO t_auth_user (user_id, authgrp_id, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('admin', 'ADMIN', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('user', 'MEMBER', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ADMIN: 관리자 전체 메뉴 권한(ADM)
INSERT INTO t_auth (menu_id, conn_id, auth_gbn, menu_yn, search_yn, mod_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'ADMIN', 'GRP', 'Y', 'Y', 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM t_menu WHERE area = 'ADM';

-- MEMBER(회원): GEN 전체(공개 + 회원전용). 로그인 시 이 메뉴 구성이 노출됨.
INSERT INTO t_auth (menu_id, conn_id, auth_gbn, menu_yn, search_yn, mod_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'MEMBER', 'GRP', 'Y', 'Y', 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM t_menu WHERE area = 'GEN';

-- GUEST(비회원): GEN 상단 공개 메뉴 = 메인(20)·모집(37)·공지사항(21)·고객센터(22)·FAQ(23).
--   ※ 취미 게시판·갤러리는 상단 메뉴에 두지 않고 '도감 중심'으로 접근(메인 카드→취미 허브→게시판).
--     공개 취미 게시판 열람 인가는 GenAccessGuard가 담당(메뉴 grant 불필요). 1:1문의(24)·마이페이지(25)는 회원 전용 제외.
-- 열람은 공개, 글쓰기·모집·신청 등 쓰기는 백엔드가 로그인 요구.
INSERT INTO t_auth (menu_id, conn_id, auth_gbn, menu_yn, search_yn, mod_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'GUEST', 'GRP', 'Y', 'Y', 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM t_menu WHERE area = 'GEN' AND menu_id IN (20, 37, 21, 22, 23);

-- ============================ 기본 게시판 (t_bbsinfo) ============================
-- 공용: id 1 공지(001)/2 FAQ(002)/3 갤러리(004)/4 1:1(003).
-- 취미 게시판(카테고리): id 5 등산/6 보드게임/7 낚시 = 일반형(001), 사진 첨부 가능. 취미 추가 시 여기 + GEN 메뉴만 등록.
INSERT INTO t_bbsinfo (bbsinfo_id, bbsinfo_nm, bbsinfo_cd, bbsinfo_desc, list_cnt,
    file_yn, file_cnt, file_size, notice_yn, new_cnt, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES
    (1, '공지사항', 'BBSINFO001', '기본 공지 게시판',  10, 'Y', 5, 10, 'Y', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (2, 'FAQ',     'BBSINFO002', 'FAQ 게시판',        10, 'N', 5, 10, 'N', 0, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (3, '갤러리',  'BBSINFO004', '갤러리 게시판',      12, 'Y', 5, 10, 'N', 0, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (4, '1:1문의', 'BBSINFO003', '1:1 문의 게시판',    10, 'Y', 5, 10, 'N', 0, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (5, '등산',     'BBSINFO001', '등산 모임·정보 공유',     10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (6, '보드게임', 'BBSINFO001', '보드게임 모임·정보 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
    (7, '낚시',     'BBSINFO001', '낚시 모임·정보 공유',     10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_bbsinfo', 'bbsinfo_id'), (SELECT MAX(bbsinfo_id) FROM t_bbsinfo));

-- ============================ 기본 취미 카탈로그 (t_hobby) ============================
-- 취미(카테고리) = 게시판. 등산1→게시판5 / 보드게임2→6 / 낚시3→7.
-- ★ GenAccessGuard.isHobbyBoard가 이 매핑으로 취미 게시판의 공개 여부를 판정한다.
--   base 설치(data.sql만)에서도 취미 커뮤니티가 동작하도록 여기서 반드시 시드한다(예전엔 sample-data.sql에만 있었음).
INSERT INTO t_hobby (hobby_id, hobby_nm, summary, intro, guide, difficulty_cd, equipment, est_cost, bbsinfo_id, sort_ordr, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, '등산', '가까운 산부터 시작하는 건강한 취미',
 '<p>등산은 장비 부담이 적고 어디서나 시작할 수 있는 대표적인 야외 취미입니다. 체력 향상과 스트레스 해소에 좋습니다.</p>',
 '<p>1) 동네 뒷산·낮은 코스부터 시작하세요.<br>2) 편한 운동화 → 익숙해지면 등산화.<br>3) 물·간식·여벌옷을 챙기세요.</p>',
 'HOBBYLV01', '운동화(입문)/등산화, 배낭, 물통', '입문 5만원 내외', 5, 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, '보드게임', '실내에서 함께 즐기는 두뇌 놀이',
 '<p>보드게임은 남녀노소 함께 즐길 수 있는 실내 취미입니다. 카페에서 부담 없이 시작할 수 있어요.</p>',
 '<p>1) 보드게임 카페에서 다양한 게임을 경험해 보세요.<br>2) 입문용(스플렌더·카탄 등)부터.<br>3) 모임에 참여하면 룰을 쉽게 배웁니다.</p>',
 'HOBBYLV01', '없음(카페 이용) / 소장 시 게임 구매', '카페 2~3시간 1만원대', 6, 2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, '낚시', '기다림의 여유를 즐기는 취미',
 '<p>낚시는 자연 속에서 여유를 즐기는 취미입니다. 민물·바다 등 종류가 다양합니다.</p>',
 '<p>1) 가까운 낚시터·좌대에서 시작.<br>2) 입문 세트(낚싯대+릴)로 충분.<br>3) 지역 물때·어종 정보를 확인하세요.</p>',
 'HOBBYLV02', '낚싯대, 릴, 채비, 아이스박스', '입문 세트 10만원 내외', 7, 3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_hobby', 'hobby_id'), (SELECT MAX(hobby_id) FROM t_hobby));

-- ============================ 이벤트 로그 샘플 (t_event_log) ============================
-- 실제 운영 시 EventLogAspect/로그인 핸들러가 자동 적재. 아래는 화면 확인용 예시(기기/UA 포함).
INSERT INTO t_event_log (event_type, user_id, target_table, target_id, device_type, user_agent, reg_dt, reg_ip) VALUES
('LOGIN',  'admin', NULL,       NULL,  'desktop', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36', NOW(), '127.0.0.1'),
('INSERT', 'admin', 't_popup',  '1',   'desktop', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36', NOW(), '127.0.0.1'),
('UPDATE', 'admin', 't_policy', '2',   'mobile',  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1', NOW(), '127.0.0.1'),
('DELETE', 'admin', 't_menu',   '25',  'desktop', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36', NOW(), '127.0.0.1');
