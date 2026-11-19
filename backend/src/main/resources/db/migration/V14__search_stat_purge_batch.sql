-- =====================================================================
-- V14 — 검색 통계 정리 배치 (CMS 1.2.40, CMS의 V18과 같다 — 정렬 순서만 7)
--
-- CMS 1.2.38에 넣은 검색 통계 집계(search_keyword_daily)는 지우는 곳이 없어 하루·검색어마다 한 줄씩 계속 쌓였다.
-- search.stat.retention-days(application.yml, 기본 365일 — 사용자 결정: 통계 화면의 최대 기간 1년과 같게)가 지난 행을 지운다.
-- 숨긴 말(search_keyword_hide)은 관리자 설정이라 지우지 않는다. 주기는 배치 작업 관리 화면에서 바꾼다.
-- 정렬 순서는 7 — 이 저장소는 모집 하루 전 알림(5)·알림 정리(6)가 앞에 있다.
-- =====================================================================

INSERT INTO batch_job (job_cd, name, description, cron_expr, auto_yn, sort_no, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('SEARCH_STAT_PURGE', '검색 통계 정리', '보존기간(search.stat.retention-days, 기본 365일)이 지난 검색어 집계 삭제(숨긴 말 목록은 남김)',
 '0 30 5 * * *', 'Y', 7, 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
