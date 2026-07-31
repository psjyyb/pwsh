# common/gen/components

사용자(gen) 영역에서 **재사용**하는 공통 UI 컴포넌트를 둔다. (배너, 카드, 리스트 등)

배치 기준(프론트 공통 구조):
- 스타일 없는 로직(훅/유틸) → `common/hooks`, `common/util` (adm·gen 공용)
- 관리자 재사용 UI → `common/adm/components`
- 사용자 재사용 UI → `common/gen/components` (여기)

영역 스타일 차이는 컴포넌트 복제 대신 `gen/theme.ts`(ConfigProvider 테마)로 처리한다.

> 현재 비어 있음(placeholder). 사용자 화면은 프로젝트마다 달라, 공통 UI는 **실제 프로젝트 도입 시** 필요에 맞춰 여기에 추가한다.
> (참고: 문서 제목은 `common/hooks/useDocumentTitle` 훅으로 `[페이지명 | 사이트명]` 자동 설정 — GenLayout이 t_config.title + 현재 메뉴명 적용.)
