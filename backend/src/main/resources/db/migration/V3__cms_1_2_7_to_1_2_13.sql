-- =====================================================================
-- V3 — CMS 1.2.7 ~ 1.2.13 (회원 라이프사이클 · 미디어 라이브러리 · 시스템 상태 ·
--      폼 파일첨부 · 신청 결과 메일)
--
-- 원래 V3~V7 다섯 파일이었다. 코드값·메뉴 몇 줄짜리가 파일 하나씩 차지해서,
-- 아직 어느 DB에도 적용되지 않은 상태에서 하나로 합쳤다(CMS 1.2.14 흡수).
--
-- ★ 합쳐도 되는 조건: 그 마이그레이션이 아직 적용되지 않았을 것.
--   Flyway는 적용된 파일의 체크섬을 저장하므로, 한 번이라도 적용된 뒤에 내용을 바꾸면
--   그 DB는 다음 기동에서 checksum mismatch로 막힌다. 합치기 전에 모든 환경에서
--   SELECT version FROM flyway_schema_history 로 확인한다.
--
-- ★ 번호 대역은 그대로다 — V1·V2=이 저장소 베이스라인 / V3~V999=CMS에서 가져온 것 /
--   V1001~=pwsh 고유. CMS 흡수분이 V3 하나로 모였을 뿐이다.
-- =====================================================================
-- ##################### (합치기 전 V3__member_lifecycle.sql) #####################
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

-- ##################### (합치기 전 V4__media_library.sql) #####################
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

-- ##################### (합치기 전 V5__system_status.sql) #####################
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

-- ##################### (합치기 전 V6__form_file_field.sql) #####################
-- =====================================================================
-- V6 — 폼 파일첨부 문항
--
-- 신청서·민원에 서류를 붙일 수 없어서 "신청 접수"로 쓰기엔 반쪽이었다.
-- 문항 유형 하나(FIELD09)를 늘리는 것으로 끝난다 — 답은 기존 form_answer_value에 파일 ID로
-- 들어가고, 파일 자체는 기존 file_ref 매핑(map_key=form_answer_id, file_type='FORM')에 붙는다.
-- 그래서 스키마 변경이 없다(고아 파일 GC·삭제 전파도 기존 규칙을 그대로 탄다).
-- =====================================================================

INSERT INTO code (code_id, p_code_id, name, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES ('FIELD09', 'FIELD00', '파일첨부', 9, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 응답에 붙은 첨부를 찾는 조회(응답 상세·삭제 전파)가 늘어난다.
-- file_ref PK가 (map_key, file_id)라 map_key 단독 조건은 선두열이라 인덱스를 타지만,
-- file_type까지 같이 거르는 형태라 복합으로 하나 둔다.
CREATE INDEX IF NOT EXISTS ix_file_ref_owner ON file_ref (map_key, file_type);

-- ##################### (합치기 전 V7__form_result_mail.sql) #####################
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

