-- 모집 장소(지도) 컬럼 추가 — 비파괴(ADD COLUMN). 기존 행은 NULL(장소 미지정)로 남는다.
ALTER TABLE t_recruit ADD COLUMN IF NOT EXISTS place_nm VARCHAR(100);
ALTER TABLE t_recruit ADD COLUMN IF NOT EXISTS addr     VARCHAR(200);
ALTER TABLE t_recruit ADD COLUMN IF NOT EXISTS lat      NUMERIC(10,7);
ALTER TABLE t_recruit ADD COLUMN IF NOT EXISTS lng      NUMERIC(10,7);

COMMENT ON COLUMN t_recruit.place_nm IS '만날 장소명(지도 선택)';
COMMENT ON COLUMN t_recruit.addr     IS '장소 주소(지도 선택)';
COMMENT ON COLUMN t_recruit.lat      IS '위도(지도 마커) — 미지정 NULL';
COMMENT ON COLUMN t_recruit.lng      IS '경도(지도 마커) — 미지정 NULL';
