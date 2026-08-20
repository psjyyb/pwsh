-- =====================================================================
-- 데모/샘플 데이터 (화면 확인용, 선택 로드) — 실제 배포 시 제외
-- 작성자는 기본계정(admin/user, DataInitializer 생성). 물리 FK 없음.
-- 실행: PGCLIENTENCODING=UTF8; psql -d pwsh -f sql/sample-data.sql
-- =====================================================================

-- 취미 카탈로그 15종(등산1 … 배드민턴22)과 연결 게시판(5~26)은 base(data.sql)로 이관됨 —
-- 여기선 취미·게시판을 만들지 않는다(중복 PK/중복 취미 방지). 아래는 그 위에 얹는 샘플 콘텐츠뿐이다.
-- 취미를 참조할 때는 name으로 찾는다(id 하드코딩은 base 시드가 바뀌면 깨진다).

-- 취미 게시판 글: 등산(5)/보드게임(6)/낚시(7)
INSERT INTO post (post_id, board_id, title, context, p_post_id, depth, sort_no,
    secret_yn, good_cnt, bad_cnt, view_cnt, notice_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, 5, '초보도 다녀온 관악산 후기', '<p>날씨 좋아서 다녀왔습니다. 초보도 충분히 가능해요!</p>', 0, 0, 1, 'N', 3, 0, 42, 'N', 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, 5, '등산화 추천 부탁드려요',       '<p>입문용 등산화 뭐가 좋을까요?</p>',              0, 0, 2, 'N', 1, 0, 15, 'N', 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, 6, '4인 추천 전략게임 뭐가 좋을까요', '<p>테라포밍마스 어떤가요?</p>',                   0, 0, 1, 'N', 2, 0, 20, 'N', 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(4, 6, '보드게임 카페 추천',            '<p>강남쪽 좋은 곳 있나요?</p>',                    0, 0, 2, 'N', 0, 0,  8, 'N', 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(5, 7, '서해 갯바위 물때표 공유',        '<p>이번 주말 물때 정리했습니다.</p>',              0, 0, 1, 'N', 5, 0, 33, 'N', 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(6, 7, '붕어 낚시 입문 질문',           '<p>떡밥 추천 좀 부탁드려요.</p>',                  0, 0, 2, 'N', 1, 0, 12, 'N', 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('post','post_id'), (SELECT MAX(post_id) FROM post));

INSERT INTO comment (post_id, context, good_cnt, bad_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, '저도 담주에 가보려구요!', 0, 0, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(1, '후기 감사합니다 :)',      0, 0, 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, '테라포밍마스 강추입니다', 0, 0, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1');

-- 모집 (취미 hobby_id: 등산1/보드게임2/낚시3)
-- area_cd=시/도 표준코드(필터 기준), region=상세 장소(자유입력). 둘에 같은 값을 넣으면 화면에 "서울 서울"로 찍힌다.
INSERT INTO recruit (recruit_id, hobby_id, title, content, capacity, area_cd, region, meet_dt,
    status_cd, view_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, 1, '주말 북한산 정기 산행',    '초보 환영합니다. 천천히 올라가요.',       4, 'AREA01', '은평구 북한산입구', '2026-08-10', 'RECRUIT01', 18, 'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, 2, '금요일 저녁 보드게임 번개', '강남 보드게임카페에서 만나요.',           6, 'AREA01', '강남역 11번 출구', '2026-08-08', 'RECRUIT01', 24, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, 3, '서해 새벽 낚시 동출',      '차량 2대 예정. 초보도 OK.',              5, 'AREA04', '연안부두', '2026-08-15', 'RECRUIT01',  9, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('recruit','recruit_id'), (SELECT MAX(recruit_id) FROM recruit));

-- 참여 신청 (본인 모집 아님)
INSERT INTO recruit_apply (recruit_id, member_id, apply_cd, apply_memo, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, 'admin', 'APPLY01', '참여하고 싶어요', 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, 'user',  'APPLY02', '저 갈게요!',     'Y', 'user',  'user',  NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('recruit_apply','apply_id'), (SELECT MAX(apply_id) FROM recruit_apply));

-- ── 러닝 샘플 글/모집 (취미·게시판은 base에 있음) ──
INSERT INTO post (post_id, board_id, title, context, p_post_id, depth, sort_no, secret_yn, good_cnt, bad_cnt, view_cnt, notice_yn, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT 7, h.board_id, '한강 야간 러닝 후기', '<p>10km 완주했습니다! 야경 최고예요.</p>', 0, 0, 1, 'N', 2, 0, 17, 'N', 'Y', 'user', 'user', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM hobby h WHERE h.name = '러닝' AND h.use_yn = 'Y';
SELECT setval(pg_get_serial_sequence('post','post_id'), (SELECT MAX(post_id) FROM post));

INSERT INTO recruit (recruit_id, hobby_id, title, content, capacity, area_cd, region, meet_dt, status_cd, view_cnt, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT 4, h.hobby_id, '주 3회 아침 러닝 크루', '초보 환영! 페이스 맞춰 함께 달려요.', 8, 'AREA01', '여의도 한강공원', '2026-08-12', 'RECRUIT01', 11, 'Y', 'user', 'user', NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM hobby h WHERE h.name = '러닝' AND h.use_yn = 'Y';
SELECT setval(pg_get_serial_sequence('recruit','recruit_id'), (SELECT MAX(recruit_id) FROM recruit));

-- ── 클라이밍·요가·캠핑 샘플 글/모집 (취미·게시판은 base에 있음) ──
-- 취미는 name으로 찾아 연결한다. 취미가 없으면 아무 행도 안 들어가고 오류도 나지 않는다.
INSERT INTO post (board_id, title, context, p_post_id, depth, sort_no, secret_yn, good_cnt, bad_cnt, view_cnt, notice_yn, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT h.board_id, v.title, v.context, 0, 0, v.sort_no, 'N', v.good, 0, v.vc, 'N', 'Y', v.reg, v.reg, NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM (VALUES
    ('클라이밍', '[후기] 클라이밍 입문 첫날', '<p>볼더링 V0~V1 완등! 손이 얼얼하지만 재밌어요.</p>', 1, 6, 40, 'user'),
    ('클라이밍', '암벽화 사이즈 조언',        '<p>발이 아파야 잘 선다는데 맞나요?</p>',            2, 1, 12, 'user'),
    ('클라이밍', '강남 실내암장 추천',        '<p>초보 강습 좋은 곳 공유합니다.</p>',              3, 2, 18, 'admin'),
    ('요가',     '[후기] 요가 한 달',        '<p>거북목이 편해졌어요. 꾸준함이 답!</p>',          1, 4, 28, 'user'),
    ('요가',     '아침 시퀀스 공유',         '<p>10분 루틴 공유합니다.</p>',                      2, 3, 22, 'admin'),
    ('요가',     '요가매트 두께 추천',       '<p>6mm vs 10mm?</p>',                               3, 1,  9, 'user'),
    ('캠핑',     '[후기] 첫 오토캠핑',       '<p>불멍하니 스트레스가 풀리네요.</p>',              1, 7, 55, 'user'),
    ('캠핑',     '입문 텐트 추천',           '<p>2~3인용 설치 쉬운 걸로요.</p>',                  2, 2, 16, 'user'),
    ('캠핑',     '초보 캠핑장 리스트',       '<p>수도권 예약 잘 되는 곳 정리.</p>',               3, 4, 30, 'admin')
  ) AS v(hobby, title, context, sort_no, good, vc, reg)
JOIN hobby h ON h.name = v.hobby AND h.use_yn = 'Y';
SELECT setval(pg_get_serial_sequence('post','post_id'), (SELECT MAX(post_id) FROM post));

INSERT INTO recruit (hobby_id, title, content, capacity, area_cd, region, meet_dt, status_cd, view_cnt, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT h.hobby_id, v.title, v.content, v.cap, v.area_cd, v.region, v.meet_dt, 'RECRUIT01', v.vc, 'Y', v.reg, v.reg, NOW(), NOW(), '127.0.0.1', '127.0.0.1'
FROM (VALUES
    ('클라이밍', '주말 볼더링 같이 하실 분',    '초보 환영! 서로 문제 봐주며 놀아요.', 0, 'AREA01', '강남 실내암장',   '2026-08-18', 14, 'user'),
    ('요가',     '평일 저녁 요가 클래스 같이',  '초보 위주 스튜디오예요.',              5, 'AREA01', '성수동 스튜디오', '2026-08-14',  8, 'admin'),
    ('캠핑',     '가을 첫 캠핑 같이 가요',      '장비 없어도 OK, 렌탈 도와드려요.',     0, 'AREA09', '가평 캠핑장',     '2026-09-05', 20, 'user')
  ) AS v(hobby, title, content, cap, area_cd, region, meet_dt, vc, reg)
JOIN hobby h ON h.name = v.hobby AND h.use_yn = 'Y';
SELECT setval(pg_get_serial_sequence('recruit','recruit_id'), (SELECT MAX(recruit_id) FROM recruit));

-- 담은 취미(인기수 데모): user/admin이 여러 취미를 담음
INSERT INTO member_hobby (member_id, hobby_id, level_cd, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
('user', 1, 'HOBBYLV01', 'Y','user','user',NOW(),NOW(),'127.0.0.1','127.0.0.1'),
('user', 2, NULL,        'Y','user','user',NOW(),NOW(),'127.0.0.1','127.0.0.1'),
('admin',1, 'HOBBYLV02', 'Y','admin','admin',NOW(),NOW(),'127.0.0.1','127.0.0.1'),
('admin',4, 'HOBBYLV01', 'Y','admin','admin',NOW(),NOW(),'127.0.0.1','127.0.0.1')
ON CONFLICT DO NOTHING;
INSERT INTO member_hobby (member_id, hobby_id, level_cd, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT 'user', hobby_id, NULL, 'Y','user','user',NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM hobby WHERE name IN ('클라이밍','캠핑') AND use_yn='Y' ON CONFLICT DO NOTHING;
INSERT INTO member_hobby (member_id, hobby_id, level_cd, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)
SELECT 'admin', hobby_id, 'HOBBYLV01', 'Y','admin','admin',NOW(),NOW(),'127.0.0.1','127.0.0.1'
FROM hobby WHERE name IN ('요가','캠핑') AND use_yn='Y' ON CONFLICT DO NOTHING;
