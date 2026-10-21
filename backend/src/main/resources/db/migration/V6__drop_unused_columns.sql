-- =====================================================================
-- V6 — CMS 1.2.20 흡수: 쓰지 않는 컬럼 정리
--
-- auth.search_yn(조회 권한)·auth.mod_yn(수정 권한): 값을 넣기만 하고 어디서도 읽지 않았다.
--   실제 권한 판정은 menu_yn 하나뿐이라, 남겨 두면 "조회만/수정 가능"이 지켜진다고 오해하게 된다.
-- config.menu_version(메뉴 캐시 버전): 조회만 하고 올리는 곳도 쓰는 곳도 없었다
--   (메뉴 트리는 권한마다 결과가 달라 애초에 캐시하지 않는다).
-- pwsh 고유 코드(모집·취미·피드 등)도 이 세 컬럼을 읽지 않음을 전수 확인했다.
--
-- ★ V2·V3의 기초데이터 INSERT(두 컬럼 포함)보다 뒤에 실행되므로 빈 DB에서도 순서가 맞다.
-- ★ 옛 빌드(이 컬럼을 SELECT·INSERT하는 코드)로 이 DB에 붙으면 환경설정 조회·권한 저장이 실패한다 —
--   새 빌드와 함께 적용한다(기동 시 Flyway가 실행하므로 보통은 자동으로 맞는다).
-- =====================================================================

ALTER TABLE auth DROP COLUMN IF EXISTS search_yn;
ALTER TABLE auth DROP COLUMN IF EXISTS mod_yn;
ALTER TABLE config DROP COLUMN IF EXISTS menu_version;
