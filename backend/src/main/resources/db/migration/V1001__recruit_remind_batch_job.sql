-- =====================================================================
-- V1001 — 모집 하루 전 알림을 배치 작업 관리로 (v0.6.34, pwsh 전용)
--
-- 모집 하루 전 알림(RecruitService.sendMeetReminders)도 다른 배치처럼 @Scheduled + yml cron(recruit.remind.cron)
-- 이었다. CMS 1.2.34(V9)로 배치 주기가 batch_job으로 옮겨 갔으니 이 배치도 같은 화면에서 주기 변경·즉시 실행·이력을 본다.
-- 주기는 기존 yml 기본값 그대로(매일 09:00 — 내일 모임 대상). yml 키는 지웠다.
-- =====================================================================

INSERT INTO batch_job (job_cd, name, description, cron_expr, auto_yn, sort_no, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('RECRUIT_REMIND', '모집 하루 전 알림', '내일 모임이 있는 모집의 주최자·수락 참여자에게 알림. 같은 날 다시 돌려도 이미 보낸 사람은 거른다',
 '0 0 9 * * *', 'Y', 5, 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
