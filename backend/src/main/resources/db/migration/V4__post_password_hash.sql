-- =============================================================
-- CMS 1.2.18 흡수 — 비밀글 비밀번호를 해시로 (기존 평문 행 전환)
-- =============================================================
-- CMS는 1.2.17까지 비밀글 비밀번호를 평문으로 저장했다. pwsh는 처음부터 BCrypt였으므로
-- 평문 행이 없으면 이 파일은 아무것도 바꾸지 않는다. 그래도 넣는 이유: CMS에서 옮겨 온 데이터나
-- 손으로 넣은 행이 평문이면 그 글이 영영 안 열린다 — 있으면 그 자리에서 해시로 바꾼다.
--
-- ★ pgcrypto의 crypt(…, gen_salt('bf'))는 bcrypt 형식($2a$)을 만든다 — Spring BCrypt가 그대로 검증한다.
--   앱의 PasswordEncoder가 DelegatingPasswordEncoder라 '{bcrypt}' 접두어를 붙여야 matches가 통과한다.
--   강도 10은 Spring BCryptPasswordEncoder 기본값과 맞춘 것이다.
-- ★ 이미 해시인 행은 건너뛴다 — 해시를 다시 해시하면 그 글은 영영 안 열린다(두 번 실행해도 안전).
UPDATE post
SET password = '{bcrypt}' || crypt(password, gen_salt('bf', 10))
WHERE password IS NOT NULL
  AND password <> ''
  AND password NOT LIKE '{bcrypt}$2%';
