# common/gen/components

사용자(gen) 영역에서 **재사용**하는 공통 UI 컴포넌트를 둔다. (배너, 카드, 리스트 등)

배치 기준(프론트 공통 구조):
- 스타일 없는 로직(훅/유틸) → `common/hooks`, `common/util` (adm·gen 공용)
- 관리자 재사용 UI → `common/adm/components`
- 사용자 재사용 UI → `common/gen/components` (여기)

영역 스타일 차이는 컴포넌트 복제 대신 `gen/theme.ts`(ConfigProvider 테마)로 처리한다.

> 현재: `UserAvatar`(닉네임·프로필 사진 + 프로필 링크), `ReportAction`(신고 버튼).
> (참고: 문서 제목은 `common/hooks/useDocumentTitle` 훅이 `[페이지명 | 사이트명]`으로 자동 설정 — 사이트명은 환경설정 값.)
