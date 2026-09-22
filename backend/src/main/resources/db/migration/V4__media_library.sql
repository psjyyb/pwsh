-- =====================================================================
-- V4 — 미디어 라이브러리   [CMS 1.2.9 흡수]
--
-- 업로드된 파일을 볼 방법이 없었다(목록 API는 있는데 부르는 화면이 없었다).
-- 또 파일은 "글에 붙는 순간"에만 생길 수 있었다 — 미리 올려두면 고아로 보고 GC가 지웠다.
--
-- ★ 스키마는 바꾸지 않는다. 라이브러리 소속은 file_ref에 map_key=0, file_type='LIBRARY'
--   매핑 한 줄로 표시한다. "모든 파일 참조는 file_ref 경유"라는 기존 원칙을 그대로 쓰면서,
--   GC의 A케이스(매핑 없는 업로드를 유예 후 삭제)에 자동으로 걸리지 않게 된다.
-- =====================================================================

COMMENT ON COLUMN file_ref.file_type IS
    '용도 구분(POST/POST_IMG/POST_EDITOR/POPUP/LOGO/BANNER/LIBRARY 등). '
    'LIBRARY는 엔티티가 아니라 "미디어 라이브러리 소속" 표시이며 map_key=0 고정';

-- 파일 하나의 사용처를 찾는 조회(사용처 목록·countActiveRefs·GC)가 늘어난다.
-- PK가 (map_key, file_id)라 file_id 단독 조건은 선두열이 아니어서 인덱스를 못 탄다.
CREATE INDEX IF NOT EXISTS ix_file_ref_file ON file_ref (file_id);

-- 프로필 사진은 file_ref를 안 거치고 member.profile_file_id로 직접 참조한다(이 저장소의 예외).
-- 미디어 라이브러리가 "이 파일 어디에 쓰이나"를 물을 때마다 member 전체를 훑게 되므로 인덱스를 둔다.
CREATE INDEX IF NOT EXISTS ix_member_profile_file ON member (profile_file_id)
    WHERE profile_file_id IS NOT NULL;

-- ── 메뉴: 시스템관리 > 미디어 라이브러리 ──
-- link_url=/adm/file → 프론트 src/adm/file/*Page.tsx 가 자동 연결된다(admScreens의 glob 규칙).
-- ★ CMS의 V4는 menu_id를 박아 넣지만 여기는 시퀀스에 맡긴다 — 이 저장소는 CMS와 menu_id 대역이
--   달라서(고유 메뉴가 더 있다) 같은 번호를 그대로 옮기면 기존 메뉴와 충돌한다.
INSERT INTO menu (p_menu_id, area, name, sort_no, conn_cd, conn_id, link_url, target_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES (1, 'ADM', '미디어 라이브러리', 11, 'MENU01', 0, '/adm/file', 'N', 'Y',
 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 권한: ADMIN 그룹에 새 메뉴 접근권. 없으면 메뉴가 사이드바에 뜨지 않는다(fail-closed).
INSERT INTO auth (menu_id, conn_id, type, menu_yn, search_yn, mod_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'ADMIN', 'GRP', 'Y', 'Y', 'Y', 'Y',
       'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM menu WHERE area = 'ADM' AND link_url = '/adm/file';
