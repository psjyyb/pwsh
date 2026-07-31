# pwsh — 개발 가이드 (Claude·개발자 공용)

**pwsh(People Who Share Hobbies)** — 취미 커뮤니티. 표준 웹 기본틀(CMS)에서 파생(패키지 `com.pwsh`, DB `pwsh`).
**아래 규약을 반드시 따른다.** 상세 규격은 [docs/standard-template-spec.md](docs/standard-template-spec.md).

## 이 프로젝트의 도메인 (신규)
- **취미 게시판(카테고리 = 게시판)**: 취미(등산·보드게임·낚시…) 1개 = 게시판(`t_bbsinfo`) 1개. GEN 메뉴 "취미게시판" 그룹에 `conn_ty=게시판`으로 연결 → `/gen/board/{id}` 범용 라우트로 동작(무코드). 취미 추가 = `data.sql`에 게시판 + 메뉴만. 작성자 표기는 **닉네임**(`t_user.nickname`, bbs/comment 조회에 `reg_nm` 조인).
- **모집**(`domain/recruit`): 모임원 모집(`t_recruit`) + 참여신청(`t_recruit_apply`, 수락/거절). `RecruitController`(5메서드, `updateRecruit{path}` ''=수정/Status=마감) + `RecruitApplyController`(peer, 댓글 패턴) + 단일 `RecruitService`. 목록/조회 공개(`SecurityConfig` permitAll), 등록/신청/수락은 로그인(인가는 서비스의 `assertOwnerOrAdmin`). 프론트 `/gen/recruit`(`gen/recruit/RecruitPage.tsx`, `genScreens` 등록).
- **셀프 회원가입**: `POST /api/auth/signup`(공개) → `AuthService.signup`이 `t_user`(MEM01) + `t_auth_user`(MEMBER) 생성. 닉네임 필수·이메일 선택, 비번 `PasswordPolicy`. 프론트 공개 라우트 `/signup`(`auth/SignupPage.tsx`).

## 스택 / 구조
- **Backend**: Spring Boot 4.1 · Java 17 · MyBatis · Spring Security + JWT · PostgreSQL. 패키지 `com.pwsh`.
- **Frontend**: React 18 · TypeScript · Vite · AntD 5 · axios · React Router. `adm`(관리자)/`gen`(사용자) 영역 분리.
- `backend/` · `frontend/` · `sql/`(schema.sql·data.sql) · `docs/`

## ★ 백엔드 도메인 패턴 (반드시 준수)
- **레이어**: Controller(매핑·입력검증만) → **단일 `@Service`**(로직+`@Transactional`, 인터페이스/Impl 분리 없음) → `CommonDAO`(MyBatis).
- **컨트롤러 5메서드 고정 + `{path}` 분기.** URL·sql_id는 **리터럴**(상수화 금지 — grep 추적성).
  - `select{Name}List{path}.do` · `select{Name}View.do` · `insert{Name}.do` · `update{Name}{path}.do` · `delete{Name}.do` (전부 `@RequestMapping("/api/adm/{name}/...")`).
  - 변형은 `{path}`로 흡수: 예 `updateUserPassword.do`(비번), `updateUserForceLogout.do`(강제로그아웃), `updateMenuOrdr.do`(정렬), `selectUserListAuthgrp.do`.
  - 입력 필수검증은 컨트롤러 진입부 `Validate.required(value, "라벨")`.
- 요청 **JSON `@RequestBody`**, 응답 **`ApiResponse{success,data,error}`**. 목록 data=`{list,totCnt,page}`. 예외는 `GlobalExceptionHandler`가 처리(컨트롤러 try/catch 금지).
- **VO**: `BaseVO` 상속(`dbKey`=자기 PK, 페이징·audit·`encKey` 포함). 필드는 **String 통일**.
- **매퍼**: `resultType`=VO, PK는 `menu_id::TEXT AS db_key`. `${}` 절대 금지(전부 `#{}`). 논리삭제 `use_yn='Y'` 필터. audit(`reg_*`/`upd_*`)는 `AuditInterceptor`가 자동 세팅.

## ★ 새 도메인 추가 순서
1. `sql/schema.sql`에 `t_{name}`(PK IDENTITY·audit·`use_yn`), 필요 시 `data.sql` 시드. 자주 조회하는 컬럼엔 인덱스.
2. `backend/.../domain/{name}/`: `service/{Name}VO`(BaseVO 상속) · `web/{Name}Controller`(5메서드) · `service/{Name}Service`(`@Service`) · `resources/mapper/{name}/{Name}_SQL.xml`(namespace `{name}DAO`).
3. `frontend/src/adm/{name}/`: `{Name}ListPage.tsx`(**폴더당 정확히 1개**, `~Page.tsx`로 끝, `export default`) · `{name}.api.ts`(`createCrudApi` + `{NAME}_LIST_URL` export).
4. **메뉴 등록**만 하면 사이드바·탭·라우팅·렌더 자동(하드코딩 레지스트리 없음): 메뉴관리에서 연결유형=`URL`, `link_url=/adm/{name}`, 아이콘 선택. 권한은 권한그룹관리에서. (원리: `admScreens.tsx`의 `import.meta.glob('./*/*Page.tsx')`)

## 인증 / 인가
- **JWT 무상태**. `t_user.token_ver`로 **단일세션(last-wins)** + 로그아웃/비번변경/강제로그아웃 시 즉시 무효화(필터가 매 요청 `ver` 대조).
- **RBAC 3계층 ADMIN/MEMBER/GUEST**(비로그인=GUEST). ① 메뉴 노출=`selectMenuTree` 권한필터 ② 관리 API=`PermissionInterceptor`(`/api/adm/**`를 `link_url` 권한으로) ③ 콘텐츠 딥링크=`GenAccessGuard`(게시판/페이지) ④ 소유자=`SecurityUtil.assertOwnerOrAdmin`.
- 비번 BCrypt·복잡도 `PasswordPolicy`(8~64자). 계정(admin/user)은 `DataInitializer`가 기동 시 생성. PII(이름/연락처 등)는 pgcrypto AES(`#{encKey}`).

## 파일 / 브랜딩
- 모든 파일 참조는 **`r_file` 매핑만**(엔티티에 직접 file_id 컬럼 두지 않음). `map_key`+`file_loc`(BBS/BBS_IMG/BBS_EDITOR/POPUP/LOGO). 고아 정리 GC — [docs/file-orphan-gc.md](docs/file-orphan-gc.md).
- 공개 이미지 `/api/pub/image/{id}`는 **연결 콘텐츠 접근권으로 인가**(순차 id 열거 IDOR 차단). 파일 관리(gc/삭제/목록)는 관리자 전용.
- **로고**: 환경설정에서 업로드(r_file `LOGO`), 미설정 시 `frontend/src/assets/logo.svg`. **메뉴 아이콘**: `t_menu.icon` 키 → 프론트 `MenuGlyph` 레지스트리.

## 실행 / 검증
- Backend: `cd backend && ./gradlew bootRun` (DB `pwsh`, dev 설정 `application-dev.yml`, 운영은 env `DB_URL`·`JWT_SECRET` 등).
- Frontend: `cd frontend && npm run dev` (`http://localhost:3000`, `/api`→8080 프록시).
- 테스트: `cd backend && ./gradlew test` — 실 PostgreSQL(`pwsh_test`, 프로파일 `test`, schema/data 자동 로드) + 실서버(RANDOM_PORT). **모킹 0**. CI: `.github/workflows/ci.yml`.
- 파일은 **UTF-8(BOM 없음)**. 한글 경로/파일명 그대로 유지. SQL 실행 시 `PGCLIENTENCODING=UTF8`.

## 문서
| 문서 | 내용 |
|---|---|
| [docs/standard-template-spec.md](docs/standard-template-spec.md) | 표준 기본틀 규격서(상세) |
| [docs/db-conventions.md](docs/db-conventions.md) | DB/테이블 설계 규칙 |
| [docs/db-conventions.md](docs/db-conventions.md) | DB 설계 규칙 |
| [docs/file-orphan-gc.md](docs/file-orphan-gc.md) | 파일 참조 모델·GC·삭제 복구 SQL |
