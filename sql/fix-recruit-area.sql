-- 모집 샘플의 지역 표기 정리 (area_cd=시/도 코드, region=상세 장소)
-- 배경: area_cd 컬럼이 나중에 추가되면서 region에 시/도명이 그대로 남아 목록에 "서울 서울"로 찍혔다.
-- sample-data.sql은 이미 수정됐고, 재적재 없이 쓰는 개발 DB는 아래 UPDATE로 맞춘다.
UPDATE t_recruit SET area_cd = 'AREA01', region = '은평구 북한산입구' WHERE title = '주말 북한산 정기 산행';
UPDATE t_recruit SET area_cd = 'AREA01', region = '강남역 11번 출구'   WHERE title = '금요일 저녁 보드게임 번개';
UPDATE t_recruit SET area_cd = 'AREA04', region = '연안부두'           WHERE title = '서해 새벽 낚시 동출';
UPDATE t_recruit SET area_cd = 'AREA01', region = '여의도 한강공원'    WHERE title = '주 3회 아침 러닝 크루';
UPDATE t_recruit SET area_cd = 'AREA01', region = '강남 실내암장'      WHERE title = '주말 볼더링 같이 하실 분';
UPDATE t_recruit SET area_cd = 'AREA01', region = '성수동 스튜디오'    WHERE title = '평일 저녁 요가 클래스 같이';
UPDATE t_recruit SET area_cd = 'AREA09', region = '가평 캠핑장'        WHERE title = '가을 첫 캠핑 같이 가요';

-- 확인
SELECT recruit_id, area_cd, region, title FROM t_recruit WHERE use_yn = 'Y' ORDER BY recruit_id;
