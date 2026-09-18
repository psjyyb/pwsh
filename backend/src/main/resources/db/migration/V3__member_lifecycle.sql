-- =====================================================================
-- V3 — 회원 라이프사이클 (휴면 전환 · 탈퇴 후 개인정보 파기)   [CMS 1.2.7 흡수]
--
-- 탈퇴가 use_yn='N'(논리삭제)뿐이라 개인정보가 영구히 남아 있었다. 보존기간이 지나면
-- 개인정보 컬럼만 비운다 — 행은 남긴다. post.reg_id·comment.reg_id·recruit.reg_id가
-- 회원 ID로 붙어 있어 행을 지우면 과거 게시글·모집의 작성자가 깨지기 때문이다.
-- =====================================================================

-- ── member: 상태 전환 시각 3종 ──
ALTER TABLE member ADD COLUMN IF NOT EXISTS dormant_dt  TIMESTAMP;
ALTER TABLE member ADD COLUMN IF NOT EXISTS withdraw_dt TIMESTAMP;
ALTER TABLE member ADD COLUMN IF NOT EXISTS destroy_dt  TIMESTAMP;
COMMENT ON COLUMN member.dormant_dt  IS '휴면 전환 시각(복구하면 NULL)';
COMMENT ON COLUMN member.withdraw_dt IS '탈퇴 시각. 여기서부터 파기 보존기간을 센다';
COMMENT ON COLUMN member.destroy_dt  IS '개인정보 파기 시각(파기된 계정은 다시 파기하지 않는다)';

-- 파기하면 이름을 비워야 하는데 CMS 원본은 name이 NOT NULL이었다. 이 저장소는 셀프가입
-- (이름 선택) 때문에 이미 NULL 허용이라 아래는 사실상 no-op이지만, CMS 마이그레이션을
-- 그대로 따라가려고 남긴다 — 나중에 CMS와 diff를 뜰 때 빠진 줄로 보이면 안 된다.
ALTER TABLE member ALTER COLUMN name DROP NOT NULL;

-- 휴면 대상 스캔(마지막 접속 기준)과 파기 대상 스캔은 매일 도는 배치라 미리 잡아둔다.
-- 대상이 아닌 행(이미 휴면/파기됨)은 부분 인덱스로 제외해 인덱스를 작게 유지한다.
CREATE INDEX IF NOT EXISTS ix_member_last_login ON member (last_login_dt)
    WHERE use_yn = 'Y' AND status_cd = 'STATUS01';
CREATE INDEX IF NOT EXISTS ix_member_withdraw ON member (withdraw_dt)
    WHERE withdraw_dt IS NOT NULL AND destroy_dt IS NULL;

-- ── config: 라이프사이클 정책값 ──
ALTER TABLE config ADD COLUMN IF NOT EXISTS dormant_days        INTEGER NOT NULL DEFAULT 365;
ALTER TABLE config ADD COLUMN IF NOT EXISTS dormant_notify_days INTEGER NOT NULL DEFAULT 30;
ALTER TABLE config ADD COLUMN IF NOT EXISTS destroy_days        INTEGER NOT NULL DEFAULT 30;
COMMENT ON COLUMN config.dormant_days        IS '미접속 휴면 전환일(0이면 휴면 전환 안 함)';
COMMENT ON COLUMN config.dormant_notify_days IS '휴면 전환 며칠 전에 안내메일을 보낼지(0이면 안 보냄)';
COMMENT ON COLUMN config.destroy_days        IS '탈퇴 후 개인정보 보존일(0이면 즉시 파기)';

-- ── 계정상태: 휴면 ──
INSERT INTO code (code_id, p_code_id, name, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES ('STATUS04', 'STATUS00', '휴면', 4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ── 이벤트 유형: 라이프사이클 행위 ──
-- ★ MEMBER_RESTORE는 이 저장소에 이미 있다(관리자 정지해제). 휴면 해제도 "정상으로 되돌린다"는
--   같은 행위라 코드를 새로 만들지 않고 이름만 넓힌다 — 새로 넣으면 PK 중복으로 기동이 막힌다.
UPDATE code SET name = '정지·휴면 해제', upd_dt = NOW() WHERE code_id = 'MEMBER_RESTORE';
INSERT INTO code (code_id, p_code_id, name, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('MEMBER_DORMANT', 'EVENT00', '휴면 전환',    10, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
('MEMBER_DESTROY', 'EVENT00', '개인정보 파기', 11, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- ── 휴면 예정 안내 메일 템플릿 ──
-- 문구는 관리자 > 시스템관리 > 메일템플릿에서 바꾼다(배포 불필요).
-- mail_template_id는 생략한다 — V2 끝에서 setval을 맞춰 뒀으므로 IDENTITY가 다음 값을 준다.
INSERT INTO mail_template (template_cd, name, subject, content, variables, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('DORMANT_NOTICE', '휴면 전환 예정 안내', '[{{siteTitle}}] 장기 미접속으로 곧 휴면 계정으로 전환됩니다',
 '<div style="margin:0;padding:24px 0;background:#f5f6f8;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center">'
 || '<table role="presentation" width="480" cellpadding="0" cellspacing="0" style="width:480px;max-width:480px;background:#ffffff;border-radius:12px;overflow:hidden;font-family:Malgun Gothic,Apple SD Gothic Neo,Arial,sans-serif;">'
 || '<tr><td style="background:#2a2f45;padding:22px 28px;color:#ffffff;font-size:19px;font-weight:700;">휴면 계정 전환 예정 안내</td></tr>'
 || '<tr><td style="padding:30px 30px 10px 30px;color:#333333;font-size:14px;line-height:1.7;">'
 || '<b>{{memberName}}</b>님, 안녕하세요.<br/>마지막 접속일로부터 오랜 기간 이용 기록이 없어 <b>{{dormantDt}}</b>에 휴면 계정으로 전환될 예정입니다.</td></tr>'
 || '<tr><td style="padding:8px 30px 10px 30px;color:#555555;font-size:13px;line-height:1.7;">'
 || '전환 전에 한 번만 로그인하시면 계속 이용하실 수 있습니다. 휴면으로 전환되면 로그인이 제한되며, 해제는 관리자에게 문의해 주세요.</td></tr>'
 || '<tr><td style="padding:10px 30px 30px 30px;"><a href="{{loginUrl}}" style="display:inline-block;background:#2a2f45;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:14px;font-weight:600;">로그인하러 가기</a></td></tr>'
 || '<tr><td style="background:#f5f6f8;padding:16px 30px;color:#999999;font-size:11.5px;line-height:1.6;">본 메일은 발신전용입니다.</td></tr>'
 || '</table></td></tr></table></div>',
 '{{siteTitle}} 사이트명 / {{memberName}} 회원 이름 / {{dormantDt}} 휴면 전환 예정일 / {{loginUrl}} 로그인 주소', 'Y',
 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
