-- 테스트 스키마 초기화: public 스키마를 통째로 비우고 재생성(테이블·pgcrypto 확장 포함 전부 제거).
-- 이후 schema.sql이 확장·테이블을 다시 만들고 data.sql이 시드를 채운다. 매 테스트 실행 시 깨끗한 상태 보장.
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
