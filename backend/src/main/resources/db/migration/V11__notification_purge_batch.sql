-- =====================================================================
-- V11 — 알림 정리 배치 (CMS 1.2.36 흡수, CMS의 V15와 같은 행 — 정렬 순서만 이 저장소의 모집 알림(5) 다음 6)
--
-- 인앱 알림(notification)은 지우는 곳이 없어 계속 쌓였다(화면은 최근 30건만 읽는다).
-- 만든 지 notification.retention-days(application.yml, 기본 90일)가 지난 알림을 읽음 여부와 상관없이 지운다.
-- 법정 보존 대상이 아니라 로그 보관 정리(LOG_RETENTION)와 따로 둔다. 주기는 배치 작업 관리 화면에서 바꾼다.
-- =====================================================================

INSERT INTO batch_job (job_cd, name, description, cron_expr, auto_yn, sort_no, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('NOTIFICATION_PURGE', '알림 정리', '보존기간(notification.retention-days, 기본 90일)이 지난 인앱 알림 삭제(읽음 여부와 상관없이)',
 '0 0 5 * * *', 'Y', 6, 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
