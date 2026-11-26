-- =====================================================================
-- V17 — 분석 도구 (CMS 1.2.44, CMS의 V21)
--
-- 사용자 결정: GA4 + 네이버 애널리틱스만(ID만 받고 스크립트는 코드가 만든다 — 관리자가 아무 코드나 넣는 '커스텀 스크립트'는 두지 않는다.
-- 관리자 계정이 털리면 모든 방문자 화면에서 그 코드가 돌고 localStorage의 로그인 토큰을 가져갈 수 있다) ·
-- 방문자가 분석 쿠키에 동의한 뒤에만 추적(사용자 화면 하단 배너, 거부·무응답이면 스크립트를 아예 불러오지 않는다).
-- 사용자 화면이 읽어야 하므로 공개 항목(public_yn='Y') — 두 ID는 어차피 페이지 소스에 드러나는 값이다.
-- 저장할 때 형식을 본다(ConfigItemService.FORMATS).
-- =====================================================================

INSERT INTO config_item (config_key, value, input_type, name, description, group_cd, sort_no, public_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('analytics.ga4-id', '', 'TEXT', 'Google Analytics 4 측정 ID',
 'G-로 시작하는 측정 ID(GA 관리 > 데이터 스트림). 비우면 쓰지 않는다. 방문자가 동의한 뒤에만 사용자 화면에 붙는다(관리자 화면 제외). 화면 이동은 GA4 향상된 측정의 "브라우저 기록 기반 페이지 변경"(기본 켜짐)으로 잡힌다.',
 'ANALYTICS', 1, 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('analytics.naver-id', '', 'TEXT', '네이버 애널리틱스 사이트 ID',
 '네이버 애널리틱스에 등록한 사이트의 ID(스크립트의 wcs_add["wa"] 값). 비우면 쓰지 않는다. 방문자가 동의한 뒤에만 사용자 화면에 붙는다(관리자 화면 제외).',
 'ANALYTICS', 2, 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
