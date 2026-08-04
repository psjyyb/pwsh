-- =====================================================================
-- 데모/샘플 데이터 (화면 확인용, 선택 로드) — 실제 배포 시 제외
-- 작성자는 기본계정(admin/user, DataInitializer 생성). 물리 FK 없음.
-- 실행: PGCLIENTENCODING=UTF8; psql -d pwsh -f sql/sample-data.sql
-- =====================================================================

-- 취미 카탈로그(등산=hobby1/보드게임=2/낚시=3)는 base(data.sql)로 이관됨 — 여기선 시드하지 않는다(중복 PK 방지).
-- 아래 샘플 글/모집은 그 취미(hobby 1/2/3)·게시판(5/6/7)을 참조한다(data.sql 선적재 전제).

-- 취미 게시판 글: 등산(5)/보드게임(6)/낚시(7)
INSERT INTO t_bbs (bbs_id, bbsinfo_id, title, context, p_bbs_id, bbs_depth, bbs_ordr,
    secret_yn, good_cnt, bad_cnt, view_cnt, notice_yn, bbs_dt, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, 5, '초보도 다녀온 관악산 후기', '<p>날씨 좋아서 다녀왔습니다. 초보도 충분히 가능해요!</p>', 0, 0, 1, 'N', 3, 0, 42, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'), 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, 5, '등산화 추천 부탁드려요',       '<p>입문용 등산화 뭐가 좋을까요?</p>',              0, 0, 2, 'N', 1, 0, 15, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'), 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, 6, '4인 추천 전략게임 뭐가 좋을까요', '<p>테라포밍마스 어떤가요?</p>',                   0, 0, 1, 'N', 2, 0, 20, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'), 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(4, 6, '보드게임 카페 추천',            '<p>강남쪽 좋은 곳 있나요?</p>',                    0, 0, 2, 'N', 0, 0,  8, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'), 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(5, 7, '서해 갯바위 물때표 공유',        '<p>이번 주말 물때 정리했습니다.</p>',              0, 0, 1, 'N', 5, 0, 33, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'), 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(6, 7, '붕어 낚시 입문 질문',           '<p>떡밥 추천 좀 부탁드려요.</p>',                  0, 0, 2, 'N', 1, 0, 12, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'), 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_bbs','bbs_id'), (SELECT MAX(bbs_id) FROM t_bbs));

INSERT INTO t_comment (bbs_id, context, good_cnt, bad_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, '저도 담주에 가보려구요!', 0, 0, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(1, '후기 감사합니다 :)',      0, 0, 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, '테라포밍마스 강추입니다', 0, 0, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 모집 (취미 hobby_id: 등산1/보드게임2/낚시3)
INSERT INTO t_recruit (recruit_id, hobby_id, title, content, capacity, region, meet_dt,
    status_cd, view_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, 1, '주말 북한산 정기 산행',    '초보 환영합니다. 천천히 올라가요.',       4, '서울', '2026-08-10', 'RECRUIT01', 18, 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, 2, '금요일 저녁 보드게임 번개', '강남 보드게임카페에서 만나요.',           6, '강남', '2026-08-08', 'RECRUIT01', 24, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, 3, '서해 새벽 낚시 동출',      '차량 2대 예정. 초보도 OK.',              5, '인천', '2026-08-15', 'RECRUIT01',  9, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_recruit','recruit_id'), (SELECT MAX(recruit_id) FROM t_recruit));

-- 참여 신청 (본인 모집 아님)
INSERT INTO t_recruit_apply (recruit_id, user_id, apply_status, apply_memo, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, 'admin', 'APPLY01', '참여하고 싶어요', 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, 'user',  'APPLY02', '저 갈게요!',     'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_recruit_apply','apply_id'), (SELECT MAX(apply_id) FROM t_recruit_apply));

-- ── 취미 추가 예시: 러닝 (게시판 8 / 취미 4) ──
INSERT INTO t_bbsinfo (bbsinfo_id, bbsinfo_nm, bbsinfo_cd, bbsinfo_desc, list_cnt, file_yn, file_cnt, file_size, notice_yn, new_cnt, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES (8, '러닝', 'BBSINFO001', '러닝 모임·정보 공유', 10, 'Y', 5, 10, 'N', 7, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_bbsinfo','bbsinfo_id'), (SELECT MAX(bbsinfo_id) FROM t_bbsinfo));

INSERT INTO t_hobby (hobby_id, hobby_nm, summary, intro, guide, difficulty_cd, equipment, est_cost, bbsinfo_id, sort_ordr, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES (4, '러닝', '언제 어디서나 가볍게 시작하는 유산소 취미',
 '<p>러닝은 장비 부담이 거의 없고 어디서나 바로 시작할 수 있는 대표적인 유산소 운동입니다.</p>',
 '<p>1) 편한 러닝화부터 준비.<br>2) 걷기 → 걷뛰기 → 달리기 순으로.<br>3) 무리하지 말고 주 2~3회부터 시작.</p>',
 'HOBBYLV01', '러닝화, 편한 복장', '러닝화 5~10만원', 8, 4, 'Y', 'system', 'system', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_hobby','hobby_id'), (SELECT MAX(hobby_id) FROM t_hobby));

INSERT INTO t_bbs (bbs_id, bbsinfo_id, title, context, p_bbs_id, bbs_depth, bbs_ordr, secret_yn, good_cnt, bad_cnt, view_cnt, notice_yn, bbs_dt, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES (7, 8, '한강 야간 러닝 후기', '<p>10km 완주했습니다! 야경 최고예요.</p>', 0, 0, 1, 'N', 2, 0, 17, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'), 'Y', 'user', 'user', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_bbs','bbs_id'), (SELECT MAX(bbs_id) FROM t_bbs));

INSERT INTO t_recruit (recruit_id, hobby_id, title, content, capacity, region, meet_dt, status_cd, view_cnt, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
VALUES (4, 4, '주 3회 아침 러닝 크루', '초보 환영! 페이스 맞춰 함께 달려요.', 8, '서울', '2026-08-12', 'RECRUIT01', 11, 'Y', 'user', 'user', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_recruit','recruit_id'), (SELECT MAX(recruit_id) FROM t_recruit));

-- ── 취미 더 추가(클라이밍·요가·캠핑): id는 IDENTITY 자동채번(CTE로 게시판↔취미↔글↔모집 연결) ──
-- 클라이밍
WITH b AS (
  INSERT INTO t_bbsinfo (bbsinfo_nm, bbsinfo_cd, bbsinfo_desc, list_cnt, file_yn, file_cnt, file_size, notice_yn, new_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
  VALUES ('클라이밍','BBSINFO001','클라이밍 정보·모임 공유',10,'Y',5,10,'N',7,'Y','system','system',NOW(),NOW(),'127.0.0.1','127.0.0.1') RETURNING bbsinfo_id
), h AS (
  INSERT INTO t_hobby (hobby_nm, summary, intro, guide, difficulty_cd, equipment, est_cost, bbsinfo_id, sort_ordr, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
  SELECT '클라이밍','실내 암장에서 시작하는 짜릿한 전신운동',
         '<p>클라이밍은 문제(루트)를 풀어가는 재미와 전신 운동 효과를 동시에 주는 실내 취미입니다.</p>',
         '<p>1) 가까운 실내 암장 원데이 클래스로 시작.<br>2) 암벽화·초크만 있으면 충분.<br>3) 볼더링부터 차근차근.</p>',
         'HOBBYLV02','암벽화, 초크백','원데이 2~3만원', b.bbsinfo_id,
         (SELECT COALESCE(MAX(sort_ordr),0)+1 FROM t_hobby WHERE use_yn='Y'),'Y','system','system',NOW(),NOW(),'127.0.0.1','127.0.0.1'
  FROM b RETURNING hobby_id, bbsinfo_id
)
INSERT INTO t_bbs (bbsinfo_id, title, context, p_bbs_id, bbs_depth, bbs_ordr, secret_yn, good_cnt, bad_cnt, view_cnt, notice_yn, bbs_dt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT h.bbsinfo_id, v.title, v.context, 0, 0, v.ordr, 'N', v.good, 0, v.vc, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'),'Y', v.reg, v.reg, NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM h, (VALUES ('[후기] 클라이밍 입문 첫날','<p>볼더링 V0~V1 완등! 손이 얼얼하지만 재밌어요.</p>',1,6,40,'user'),
                ('암벽화 사이즈 조언','<p>발이 아파야 잘 선다는데 맞나요?</p>',2,1,12,'user'),
                ('강남 실내암장 추천','<p>초보 강습 좋은 곳 공유합니다.</p>',3,2,18,'admin')) AS v(title,context,ordr,good,vc,reg);
INSERT INTO t_recruit (hobby_id, title, content, capacity, region, meet_dt, status_cd, view_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT hobby_id, '주말 볼더링 같이 하실 분','초보 환영! 서로 문제 봐주며 놀아요.', 0, '서울', '2026-08-18', 'RECRUIT01', 14, 'Y','user','user',NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM t_hobby WHERE hobby_nm='클라이밍' AND use_yn='Y' ORDER BY hobby_id DESC LIMIT 1;

-- 요가
WITH b AS (
  INSERT INTO t_bbsinfo (bbsinfo_nm, bbsinfo_cd, bbsinfo_desc, list_cnt, file_yn, file_cnt, file_size, notice_yn, new_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
  VALUES ('요가','BBSINFO001','요가 정보·모임 공유',10,'Y',5,10,'N',7,'Y','system','system',NOW(),NOW(),'127.0.0.1','127.0.0.1') RETURNING bbsinfo_id
), h AS (
  INSERT INTO t_hobby (hobby_nm, summary, intro, guide, difficulty_cd, equipment, est_cost, bbsinfo_id, sort_ordr, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
  SELECT '요가','몸과 마음을 함께 돌보는 균형 운동',
         '<p>요가는 유연성·코어 근력·호흡까지 아우르는 취미입니다. 부담 없이 시작할 수 있어요.</p>',
         '<p>1) 매트 하나면 집에서도 가능.<br>2) 기초 시퀀스부터.<br>3) 무리한 동작은 천천히.</p>',
         'HOBBYLV01','요가매트, 편한 복장','매트 2~4만원', b.bbsinfo_id,
         (SELECT COALESCE(MAX(sort_ordr),0)+1 FROM t_hobby WHERE use_yn='Y'),'Y','system','system',NOW(),NOW(),'127.0.0.1','127.0.0.1'
  FROM b RETURNING hobby_id, bbsinfo_id
)
INSERT INTO t_bbs (bbsinfo_id, title, context, p_bbs_id, bbs_depth, bbs_ordr, secret_yn, good_cnt, bad_cnt, view_cnt, notice_yn, bbs_dt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT h.bbsinfo_id, v.title, v.context, 0, 0, v.ordr, 'N', v.good, 0, v.vc, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'),'Y', v.reg, v.reg, NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM h, (VALUES ('[후기] 요가 한 달','<p>거북목이 편해졌어요. 꾸준함이 답!</p>',1,4,28,'user'),
                ('아침 시퀀스 공유','<p>10분 루틴 공유합니다.</p>',2,3,22,'admin'),
                ('요가매트 두께 추천','<p>6mm vs 10mm?</p>',3,1,9,'user')) AS v(title,context,ordr,good,vc,reg);
INSERT INTO t_recruit (hobby_id, title, content, capacity, region, meet_dt, status_cd, view_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT hobby_id, '평일 저녁 요가 클래스 같이','초보 위주 스튜디오예요.', 5, '서울', '2026-08-14', 'RECRUIT01', 8, 'Y','admin','admin',NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM t_hobby WHERE hobby_nm='요가' AND use_yn='Y' ORDER BY hobby_id DESC LIMIT 1;

-- 캠핑
WITH b AS (
  INSERT INTO t_bbsinfo (bbsinfo_nm, bbsinfo_cd, bbsinfo_desc, list_cnt, file_yn, file_cnt, file_size, notice_yn, new_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
  VALUES ('캠핑','BBSINFO001','캠핑 정보·모임 공유',10,'Y',5,10,'N',7,'Y','system','system',NOW(),NOW(),'127.0.0.1','127.0.0.1') RETURNING bbsinfo_id
), h AS (
  INSERT INTO t_hobby (hobby_nm, summary, intro, guide, difficulty_cd, equipment, est_cost, bbsinfo_id, sort_ordr, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
  SELECT '캠핑','자연 속에서 쉬어가는 힐링 취미',
         '<p>캠핑은 자연에서 하루를 보내며 재충전하는 취미입니다. 장비는 차차 갖춰도 됩니다.</p>',
         '<p>1) 처음엔 오토캠핑장부터.<br>2) 렌탈로 체험 후 구매.<br>3) 안전·화기 수칙 준수.</p>',
         'HOBBYLV02','텐트, 침낭, 랜턴, 의자','입문 세트 20만원 내외', b.bbsinfo_id,
         (SELECT COALESCE(MAX(sort_ordr),0)+1 FROM t_hobby WHERE use_yn='Y'),'Y','system','system',NOW(),NOW(),'127.0.0.1','127.0.0.1'
  FROM b RETURNING hobby_id, bbsinfo_id
)
INSERT INTO t_bbs (bbsinfo_id, title, context, p_bbs_id, bbs_depth, bbs_ordr, secret_yn, good_cnt, bad_cnt, view_cnt, notice_yn, bbs_dt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT h.bbsinfo_id, v.title, v.context, 0, 0, v.ordr, 'N', v.good, 0, v.vc, 'N', TO_CHAR(NOW(),'YYYY-MM-DD'),'Y', v.reg, v.reg, NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM h, (VALUES ('[후기] 첫 오토캠핑','<p>불멍하니 스트레스가 풀리네요.</p>',1,7,55,'user'),
                ('입문 텐트 추천','<p>2~3인용 설치 쉬운 걸로요.</p>',2,2,16,'user'),
                ('초보 캠핑장 리스트','<p>수도권 예약 잘 되는 곳 정리.</p>',3,4,30,'admin')) AS v(title,context,ordr,good,vc,reg);
INSERT INTO t_recruit (hobby_id, title, content, capacity, region, meet_dt, status_cd, view_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT hobby_id, '가을 첫 캠핑 같이 가요','장비 없어도 OK, 렌탈 도와드려요.', 0, '경기', '2026-09-05', 'RECRUIT01', 20, 'Y','user','user',NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM t_hobby WHERE hobby_nm='캠핑' AND use_yn='Y' ORDER BY hobby_id DESC LIMIT 1;

-- 담은 취미(인기수 데모): user/admin이 여러 취미를 담음
INSERT INTO t_user_hobby (user_id, hobby_id, level_cd, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('user', 1, 'HOBBYLV01', 'Y','user','user',NOW(),NOW(),'127.0.0.1','127.0.0.1'),
('user', 2, NULL,        'Y','user','user',NOW(),NOW(),'127.0.0.1','127.0.0.1'),
('admin',1, 'HOBBYLV02', 'Y','admin','admin',NOW(),NOW(),'127.0.0.1','127.0.0.1'),
('admin',4, 'HOBBYLV01', 'Y','admin','admin',NOW(),NOW(),'127.0.0.1','127.0.0.1')
ON CONFLICT DO NOTHING;
INSERT INTO t_user_hobby (user_id, hobby_id, level_cd, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT 'user', hobby_id, NULL, 'Y','user','user',NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM t_hobby WHERE hobby_nm IN ('클라이밍','캠핑') AND use_yn='Y' ON CONFLICT DO NOTHING;
INSERT INTO t_user_hobby (user_id, hobby_id, level_cd, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT 'admin', hobby_id, 'HOBBYLV01', 'Y','admin','admin',NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM t_hobby WHERE hobby_nm IN ('요가','캠핑') AND use_yn='Y' ON CONFLICT DO NOTHING;
