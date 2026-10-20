-- =====================================================================
-- V5 — CMS 1.2.19 흡수: 좋아요에 추천/비추천 구분 추가
--
-- pwsh는 처음부터 content_like(좋아요 = 추천만)가 있었다. CMS 1.2.19의 V6는 같은 테이블을
-- CREATE TABLE IF NOT EXISTS로 만들어 여기선 아무것도 하지 않으므로, 구분 컬럼만 따로 붙인다.
-- ★ 기본값 GOOD — 기존 좋아요 행은 그대로 추천으로 읽힌다(good_cnt도 이미 그 수만큼 올라가 있다).
-- =====================================================================

ALTER TABLE content_like ADD COLUMN IF NOT EXISTS like_type VARCHAR(10) NOT NULL DEFAULT 'GOOD';
COMMENT ON TABLE  content_like IS '게시글·댓글 추천/비추천(1인 1표). 취소 시 행 삭제';
COMMENT ON COLUMN content_like.like_type IS '추천 GOOD / 비추천 BAD';
