-- =====================================================================
-- V10 — CMS 1.2.35 흡수(CMS의 V13 중 이 저장소에 없는 부분): 회원가입 켜기·끄기 · 인증 코드 해시 저장
--
-- 가입·비밀번호 찾기·약관 동의 이력(member_policy)·코드 메일 템플릿은 이 저장소가 먼저 가지고 있어 CMS V13을 그대로 가져오지 않는다.
-- 여기서 바꾸는 것은 둘뿐이다:
--   ① config.signup_yn — CMS는 기본 꺼짐('N')이지만 이 저장소는 가입이 늘 열린 커뮤니티라 기본 켜짐('Y')
--   ② email_verification.code(평문) → code_hash(BCrypt). DB를 읽을 수 있는 사람이 남의 가입·비밀번호 재설정을 대신하지 못하게
-- =====================================================================

ALTER TABLE config ADD COLUMN IF NOT EXISTS signup_yn VARCHAR(1) NOT NULL DEFAULT 'Y';
COMMENT ON COLUMN config.signup_yn IS '셀프 회원가입 허용(Y/N, 기본 Y) — 켤 때 메일 발송이 켜져 있어야 한다(인증 코드)';

-- 남아 있던 코드는 평문이라 옮기지 않고 비운다(몇 분짜리 임시 행 — 가입·재설정 중이던 사람은 코드를 다시 받는다)
DELETE FROM email_verification;
ALTER TABLE email_verification DROP COLUMN IF EXISTS code;
ALTER TABLE email_verification ADD COLUMN code_hash TEXT NOT NULL;
COMMENT ON COLUMN email_verification.code_hash IS '6자리 코드의 BCrypt 해시(평문 저장 안 함)';
COMMENT ON COLUMN email_verification.attempt_cnt IS '틀린 횟수 — 5회면 맞는 코드도 거절';
COMMENT ON COLUMN email_verification.expire_dt IS '만료 시각. 메일 발송에 실패하면 바로 만료시킨다(행은 재발송 제한 근거로 남김)';
