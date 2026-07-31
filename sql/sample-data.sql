-- =====================================================================
-- 데모/샘플 데이터 (화면 확인용, 선택 로드) — 실제 배포 시 제외
-- 작성자는 기본계정(admin/user, DataInitializer 생성). 물리 FK 없음.
-- 실행: PGCLIENTENCODING=UTF8; psql -d pwsh -f sql/sample-data.sql
-- =====================================================================

-- 취미 카탈로그(등산=hobby1/보드게임=2/낚시=3). bbsinfo_id로 소통 게시판(5/6/7) 연결.
INSERT INTO t_hobby (hobby_id, hobby_nm, summary, intro, guide, difficulty_cd, equipment, est_cost, bbsinfo_id, sort_ordr, use_yn,
    reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip) VALUES
(1, '등산', '가까운 산부터 시작하는 건강한 취미',
 '<p>등산은 장비 부담이 적고 어디서나 시작할 수 있는 대표적인 야외 취미입니다. 체력 향상과 스트레스 해소에 좋습니다.</p>',
 '<p>1) 동네 뒷산·낮은 코스부터 시작하세요.<br>2) 편한 운동화 → 익숙해지면 등산화.<br>3) 물·간식·여벌옷을 챙기세요.</p>',
 'HOBBYLV01', '운동화(입문)/등산화, 배낭, 물통', '입문 5만원 내외', 5, 1, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(2, '보드게임', '실내에서 함께 즐기는 두뇌 놀이',
 '<p>보드게임은 남녀노소 함께 즐길 수 있는 실내 취미입니다. 카페에서 부담 없이 시작할 수 있어요.</p>',
 '<p>1) 보드게임 카페에서 다양한 게임을 경험해 보세요.<br>2) 입문용(스플렌더·카탄 등)부터.<br>3) 모임에 참여하면 룰을 쉽게 배웁니다.</p>',
 'HOBBYLV01', '없음(카페 이용) / 소장 시 게임 구매', '카페 2~3시간 1만원대', 6, 2, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1'),
(3, '낚시', '기다림의 여유를 즐기는 취미',
 '<p>낚시는 자연 속에서 여유를 즐기는 취미입니다. 민물·바다 등 종류가 다양합니다.</p>',
 '<p>1) 가까운 낚시터·좌대에서 시작.<br>2) 입문 세트(낚싯대+릴)로 충분.<br>3) 지역 물때·어종 정보를 확인하세요.</p>',
 'HOBBYLV02', '낚싯대, 릴, 채비, 아이스박스', '입문 세트 10만원 내외', 7, 3, 'Y', 'admin', 'admin', NOW(), NOW(), '127.0.0.1', '127.0.0.1');
SELECT setval(pg_get_serial_sequence('t_hobby','hobby_id'), (SELECT MAX(hobby_id) FROM t_hobby));

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
