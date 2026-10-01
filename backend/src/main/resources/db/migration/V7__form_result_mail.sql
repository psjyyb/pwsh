-- =====================================================================
-- V7 — 신청 결과 메일 자동발송
--
-- 접수·처리는 되는데 신청자에게 알릴 방법이 없었다. 관리자가 상태를 바꿔도(완료/반려)
-- 신청자는 다시 들어와 확인할 화면조차 없다.
--
-- 메일 엔진(1.2.3)과 폼(1.2.5)이 이미 있으므로 "상태가 바뀌면 템플릿으로 보낸다"만 이으면 된다.
-- =====================================================================

-- 설문에는 결과 메일이 필요 없다 → 폼마다 켜고 끈다. 기본은 꺼짐(기존 폼의 동작을 바꾸지 않는다).
ALTER TABLE form ADD COLUMN IF NOT EXISTS result_mail_yn VARCHAR(1) NOT NULL DEFAULT 'N';
COMMENT ON COLUMN form.result_mail_yn IS '처리상태 변경 시 신청자에게 결과 메일 발송(Y/N)';

-- ── 결과 안내 메일 템플릿 ──
-- 상태별로 템플릿을 나누지 않는다 — 문구 차이는 {{statusName}}·{{adminMemo}}로 흡수되고,
-- 나누면 상태를 하나 늘릴 때마다 템플릿을 새로 만들어야 한다(문구는 관리자 화면에서 고친다).
INSERT INTO mail_template (template_cd, name, subject, content, variables, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('FORM_RESULT', '신청 처리결과 안내', '[{{siteTitle}}] {{formTitle}} 처리결과 안내',
 '<div style="margin:0;padding:24px 0;background:#f5f6f8;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center">'
 || '<table role="presentation" width="480" cellpadding="0" cellspacing="0" style="width:480px;max-width:480px;background:#ffffff;border-radius:12px;overflow:hidden;font-family:Malgun Gothic,Apple SD Gothic Neo,Arial,sans-serif;">'
 || '<tr><td style="background:#2a2f45;padding:22px 28px;color:#ffffff;font-size:19px;font-weight:700;">처리결과 안내</td></tr>'
 || '<tr><td style="padding:30px 30px 10px 30px;color:#333333;font-size:14px;line-height:1.7;">'
 || '<b>{{formTitle}}</b>에 접수하신 건의 처리상태가 <b>{{statusName}}</b>(으)로 변경되었습니다.</td></tr>'
 || '<tr><td style="padding:4px 30px 10px 30px;color:#555555;font-size:13px;line-height:1.7;">접수일시: {{regDt}}</td></tr>'
 || '<tr><td style="padding:0 30px 24px 30px;"><div style="background:#f5f6f8;border-radius:8px;padding:14px 16px;color:#333333;font-size:13px;line-height:1.7;white-space:pre-line;">{{adminMemo}}</div></td></tr>'
 || '<tr><td style="background:#f5f6f8;padding:16px 30px;color:#999999;font-size:11.5px;line-height:1.6;">본 메일은 발신전용입니다.</td></tr>'
 || '</table></td></tr></table></div>',
 '{{siteTitle}} 사이트명 / {{formTitle}} 폼 제목 / {{statusName}} 처리상태 / {{adminMemo}} 담당자 메모 / {{regDt}} 접수일시', 'Y',
 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
