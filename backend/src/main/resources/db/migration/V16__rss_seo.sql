-- =====================================================================
-- V16 — RSS · SEO (CMS 1.2.43, CMS의 V20)
--
-- 사용자 결정: RSS는 비로그인이 읽는 게시판 전부 자동(1:1 문의 제외 — 이 저장소는 취미 게시판 포함) · 제목 + 본문 앞부분 ·
-- SEO 기본 묶음(사이트 설명·키워드 메타, 화면별 제목·설명, sitemap.xml, robots.txt) · 링크의 사이트 주소는 확장설정 값만
-- (비면 RSS·sitemap을 끄고 설치 점검에 경고). 요청 주소(Host 헤더)로 링크를 만들지 않는다 — 앞단 캐시 구성에 따라 조작된 Host가 링크로 박힐 수 있다.
-- 설명·키워드 기본값은 index.html의 메타와 같게 둔다(적용 직후 화면이 바뀌지 않게).
-- =====================================================================

INSERT INTO config_item (config_key, value, input_type, name, description, group_cd, sort_no, public_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('site.url', '', 'TEXT', '사이트 주소',
 'https://도메인 형태(끝의 / 는 빼도 된다). RSS·sitemap.xml·robots.txt의 링크를 이 주소로 만든다. 비우면 RSS·sitemap을 내지 않는다.',
 'SITE', 3, 'N', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('seo.description', '취미로 만나는 사람들 — pwsh 취미 커뮤니티', 'TEXTAREA', '사이트 설명(검색엔진)',
 '검색 결과에 보이는 사이트 설명(meta description). 게시글 화면은 그 글의 앞부분으로 바뀐다.',
 'SEO', 1, 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('seo.keywords', '취미,커뮤니티,모임,모집', 'TEXT', '사이트 키워드(검색엔진)',
 '쉼표로 구분(meta keywords). 비우면 넣지 않는다.',
 'SEO', 2, 'Y', 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
