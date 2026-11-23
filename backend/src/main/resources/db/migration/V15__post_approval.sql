-- =====================================================================
-- V15 — 게시 승인 (CMS 1.2.41, CMS의 V19 — 메뉴 부모만 주소로 찾는다)
--
-- 사용자 결정: 게시판별로 켠다(기본 꺼짐) · 승인된 글을 작성자가 고치면 다시 승인 대기 · 반려는 사유 입력 + 작성자 알림 ·
-- 처리는 별도 메뉴 '게시 승인'(모든 게시판의 대기 글을 모아 본다).
-- 승인을 쓰는 게시판의 회원 글은 승인 전까지 작성자·관리자만 본다(목록·상세·통합검색·댓글·추천·첨부 모두). 관리자 글은 바로 승인.
-- =====================================================================

ALTER TABLE board ADD COLUMN IF NOT EXISTS approval_yn VARCHAR(1) NOT NULL DEFAULT 'N';
COMMENT ON COLUMN board.approval_yn IS '게시 승인 사용(Y/N) — Y면 회원 글은 관리자 승인 전까지 작성자·관리자만 본다';

-- ★ 기본값 승인(APPROVAL02): 기존 글은 모두 공개 그대로다. 승인 대기로 넣는 곳은 PostService(게시판 설정을 본다) 한 곳뿐이다.
ALTER TABLE post ADD COLUMN IF NOT EXISTS approval_cd   VARCHAR(15)  NOT NULL DEFAULT 'APPROVAL02';
ALTER TABLE post ADD COLUMN IF NOT EXISTS approval_memo VARCHAR(500);
ALTER TABLE post ADD COLUMN IF NOT EXISTS approval_id   VARCHAR(30);
ALTER TABLE post ADD COLUMN IF NOT EXISTS approval_dt   TIMESTAMP;
COMMENT ON COLUMN post.approval_cd   IS '게시 승인 상태(code APPROVAL00): 승인 대기/승인/반려 — 승인이 아니면 작성자·관리자만 본다';
COMMENT ON COLUMN post.approval_memo IS '반려 사유(작성자·관리자만 본다). 작성자가 고쳐 다시 대기가 되면 지운다';
COMMENT ON COLUMN post.approval_id   IS '마지막으로 승인·반려한 관리자';
COMMENT ON COLUMN post.approval_dt   IS '마지막 승인·반려 시각 — 대기인데 값이 있으면 처리 뒤 고쳐 다시 올린 글';
-- 승인 화면은 대기 글만 훑는다 — 대부분인 승인 글은 색인에서 뺀다
CREATE INDEX IF NOT EXISTS ix_post_approval_wait ON post (approval_cd, upd_dt) WHERE approval_cd <> 'APPROVAL02';

-- ── 공통코드 ──
INSERT INTO code (code_id, p_code_id, name, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT 'APPROVAL00', 'ROOT', '게시승인', (SELECT COALESCE(MAX(sort_no), 0) FROM code WHERE p_code_id = 'ROOT') + 1,
       'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1';

INSERT INTO code (code_id, p_code_id, name, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('APPROVAL01', 'APPROVAL00', '승인 대기', 1, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('APPROVAL02', 'APPROVAL00', '승인',      2, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('APPROVAL03', 'APPROVAL00', '반려',      3, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ── 메뉴: 게시판 관리 > 게시 승인 ──
-- 부모는 '게시판 설정'(/adm/board)과 같은 그룹으로 주소로 찾는다(CMS와 메뉴 번호가 다를 수 있다). 순서는 그 그룹의 맨 뒤.
-- menu_id는 적지 않는다 — 권한은 주소로 찾아 붙인다.
INSERT INTO menu (p_menu_id, area, name, sort_no, conn_cd, conn_id, link_url, icon, target_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT g.p_menu_id, 'ADM', '게시 승인',
       (SELECT COALESCE(MAX(m.sort_no), 0) + 1 FROM menu m WHERE m.p_menu_id = g.p_menu_id AND m.area = 'ADM'),
       'MENU01', 0, '/adm/postapproval', 'check', 'N', 'Y',
       'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM (SELECT p_menu_id FROM menu WHERE area = 'ADM' AND link_url = '/adm/board' ORDER BY menu_id LIMIT 1) g;

INSERT INTO auth (menu_id, conn_id, type, menu_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT menu_id, 'ADMIN', 'GRP', 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM menu WHERE area = 'ADM' AND link_url = '/adm/postapproval';
