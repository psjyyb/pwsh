-- =====================================================================
-- V5 — 시스템 상태 화면 메뉴   [CMS 1.2.11 흡수]
--
-- "지금 어느 버전이 떠 있나, 마이그레이션은 어디까지 적용됐나, 디스크는 남았나, 배치는 언제 도나"를
-- 물을 곳이 없었다. 서버에 들어가 로그를 뒤져야 알 수 있었다.
--
-- 스키마 변경은 없다(전부 읽기 전용 조회). 메뉴·권한 시드만 넣는다.
-- =====================================================================

-- ── 메뉴: 시스템관리 > 시스템 상태 ──
-- link_url=/adm/system → 프론트 src/adm/system/*Page.tsx 가 자동 연결된다(admScreens의 glob 규칙).
-- ★ CMS의 V5는 menu_id를 박아 넣지만 여기는 시퀀스에 맡긴다 — 이 저장소는 CMS와 menu_id 대역이
--   달라서(고유 메뉴가 더 있다) 같은 번호를 그대로 옮기면 기존 메뉴와 충돌한다(V4와 같은 이유).
INSERT INTO menu (p_menu_id, area, name, sort_no, conn_cd, conn_id, link_url, target_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES (1, 'ADM', '시스템 상태', 12, 'MENU01', 0, '/adm/system', 'N', 'Y',
 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 권한: ADMIN 그룹에 새 메뉴 접근권. 없으면 메뉴가 사이드바에 뜨지 않는다(fail-closed).
INSERT INTO auth (menu_id, conn_id, type, menu_yn, search_yn, mod_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'ADMIN', 'GRP', 'Y', 'Y', 'Y', 'Y',
       'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM menu WHERE area = 'ADM' AND link_url = '/adm/system';
