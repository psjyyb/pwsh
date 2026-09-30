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
