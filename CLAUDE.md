# pwsh — 개발 가이드 (Claude·개발자 공용)

**pwsh(People Who Share Hobbies)** — 취미 커뮤니티. 직접 만든 표준 웹 기본틀(CMS) 위에 얹은 서비스(패키지 `com.pwsh`).
**아래 규약을 반드시 따른다.**

## 이 프로젝트의 도메인
- **취미 게시판**: 취미(등산·보드게임·낚시…) 1개 = 게시판 1개. 게시판/댓글/첨부/권한은 틀의 기능을 그대로 쓴다. 취미 추가 = 기초데이터에 게시판 + 메뉴 등록만(코드 수정 없음). 작성자 표기는 **닉네임**.
- **모집**(`domain/recruit`): 모임원 모집 + 참여신청(수락/거절/대기) + 참석 기록 + **확정자 단체 대화**(SSE 실시간) + **다음 회차 복제**. `RecruitController`(5메서드, `insertRecruit{variant}` ''=신규/Copy=복제, `updateRecruit{variant}` ''=수정/Status=마감) + `RecruitApplyController`·`RecruitChatController`(peer) + 단일 `RecruitService`. 목록/조회는 공개, 등록·신청·수락은 로그인(세부 인가는 서비스의 `assertOwnerOrAdmin`).
- **부가 도메인**: 알림·쪽지(SSE)·후기·북마크·차단·신고·통합검색·내 취미 피드·활동 배지·마이페이지(내 일정 캘린더).
- **셀프 회원가입**: `POST /api/auth/signup`(공개, 이메일 인증 + 닉네임 필수). 프론트 공개 라우트 `/signup`.

## 스택 / 구조
- **Backend**: Spring Boot 4.1 · Java 17 · MyBatis · Spring Security + JWT · PostgreSQL. 패키지 `com.pwsh`.
- **Frontend**: React 18 · TypeScript · Vite · AntD 5 · axios · React Router. `adm`(관리자)/`gen`(사용자) 영역 분리.
- `backend/` · `frontend/` · `sql/`(schema.sql·data.sql)

## ★ 백엔드 도메인 패턴 (반드시 준수)
- **레이어**: Controller(매핑·입력검증만) → **단일 `@Service`**(로직+`@Transactional`, 인터페이스/Impl 분리 없음) → `CommonDAO`(MyBatis).
- **컨트롤러 5메서드 고정 + `{variant}` 분기.** URL·sql_id는 **리터럴**(상수화 금지 — grep 추적성).
  - `select{Name}List{variant}.do` · `select{Name}View.do` · `insert{Name}{variant}.do` · `update{Name}{variant}.do` · `delete{Name}.do` (전부 `@RequestMapping("/api/adm/{name}/...")`).
  - 변형은 `{variant}`로 흡수: 예 `updateMemberPassword.do`(비번), `updateMemberForceLogout.do`(강제로그아웃), `updateMenuSort.do`(정렬).
  - 입력 필수검증은 컨트롤러 진입부 `Validate.required(value, "라벨")`.
- 요청 **JSON `@RequestBody`**, 응답 **`ApiResponse{success,data,error}`**. 목록 data=`{list,totalCount,page}`. 예외는 `GlobalExceptionHandler`가 처리(컨트롤러 try/catch 금지).
- **VO**: `BaseVO` 상속(`rowId`=자기 PK, 페이징·audit·암호화키 포함). 필드는 **String 통일**.
- **매퍼**: `resultType`=VO, PK는 문자열로 캐스팅해 VO의 `rowId`로 받는다. `${}` 절대 금지(전부 `#{}`). 논리삭제 플래그로 항상 필터. 등록/수정 이력 값은 `AuditInterceptor`가 자동 세팅.

## ★ 새 도메인 추가 순서
1. `sql/schema.sql`에 테이블 1개(자동증가 PK + 등록/수정 이력 + 논리삭제 플래그 — 기존 테이블 형태를 그대로 따른다), 필요 시 `data.sql`에 시드. 자주 조회하는 컬럼엔 인덱스.
2. `backend/.../domain/{name}/`: `service/{Name}VO`(BaseVO 상속) · `web/{Name}Controller`(5메서드) · `service/{Name}Service`(`@Service`) · `resources/mapper/{name}/{Name}_SQL.xml`(namespace `{name}DAO`).
3. `frontend/src/adm/{name}/`: `{Name}ListPage.tsx`(**폴더당 정확히 1개**, `~Page.tsx`로 끝, `export default`) · `{name}.api.ts`(`createCrudApi` + `{NAME}_LIST_URL` export).
4. **메뉴 등록**만 하면 사이드바·탭·라우팅·렌더 자동(하드코딩 레지스트리 없음): 메뉴관리에서 연결유형=`URL`, 주소 `/adm/{name}`, 아이콘 선택. 권한은 권한그룹관리에서. (원리: `admScreens.tsx`의 `import.meta.glob('./*/*Page.tsx')`)

## 인증 / 인가
- **JWT 무상태**. 사용자별 토큰 버전으로 **단일세션(last-wins)** + 로그아웃/비번변경/강제로그아웃 시 즉시 무효화(필터가 매 요청 대조).
- **RBAC 3계층 ADMIN/MEMBER/GUEST**(비로그인=GUEST). ① 메뉴 노출=메뉴 조회 시 권한 필터 ② 관리 API=`PermissionInterceptor`(`/api/adm/**`를 메뉴 URL 권한으로. 사용자 콘텐츠 API는 예외 목록으로 통과시키고 서비스가 인가) ③ 콘텐츠 딥링크=`GenAccessGuard` ④ 소유자=`SecurityUtil.assertOwnerOrAdmin`.
- 비번 BCrypt·복잡도 `PasswordPolicy`(8~64자). 계정(admin/user)은 `DataInitializer`가 기동 시 생성. 개인정보(이름·연락처 등)는 pgcrypto AES(`#{cryptoKey}`).
- **공개 식별자**: 클라이언트에 로그인 ID를 내려보내지 않는다. 회원 지목은 12자리 `handle`, 본인 판정은 서버가 계산한 `mineYn`.

## 파일 / 실시간 / 브랜딩
- 모든 파일 참조는 **매핑 테이블 경유만**(엔티티에 직접 파일 ID 컬럼을 두지 않는다). 용도 코드(POST/POST_IMG/POST_EDITOR/POPUP/LOGO)로 구분.
- **고아 파일 GC**: 업로드 후 미저장(기본 24h 유예)·삭제된 엔티티의 파일(기본 180일 보존)을 스케줄러가 회수. 설정은 `application.yml`의 `file.gc.*`, 수동 실행은 `POST /api/adm/file/gc.do`(관리자).
  - ★ **삭제 전파 필수**: 파일을 매핑하는 도메인은 엔티티 삭제 시 `fileDAO.deactivateFilesByOwner`로 파일을 비활성화해야 한다. 누락하면 GC에 걸리지 않아 영구 누수된다(컴파일러가 못 잡음).
  - ⚠️ `FileController.saveFileMapping`은 매핑에서 빠진 파일을 **즉시 물리 삭제**한다. 복구 불가.
  - 휴지통 UI는 없다. 복구는 개발자가 논리삭제 플래그를 되돌리는 SQL로 처리(GC 실행 후에는 파일 복구 불가, 본문만 복구).
- 공개 이미지 `/api/pub/image/{id}`와 **첨부 목록 조회**는 **연결 콘텐츠 접근권으로 인가**(순차 id 열거 차단) — 공개 글이면 비로그인도 보이고, 권한 없는 게시판이면 로그인 회원이라도 파일명조차 안 준다. 파일 관리(gc/삭제/목록)는 관리자 전용.
  - ★ 공개 화면이 부르는 조회 API는 `SecurityConfig` permitAll에 넣어야 한다. 하나라도 빠지면 401 → 프론트 인터셉터가 로그인 화면으로 보내 **화면 전체를 못 본다**(회귀 방지: `GuestPublicPageTest`).
- **실시간(SSE)**: `RealtimeService`가 사용자별 연결을 들고 "새 게 있다"는 이벤트 이름만 푸시한다(본문 없음 — 인가는 조회 API 한 곳에만). 프론트는 `useEventStream`(fetch POST + Authorization 헤더, 자동 재연결), 끊기면 폴링으로 대체. 단일 JVM 한정.
- **로고**: 환경설정에서 업로드, 미설정 시 `frontend/src/assets/logo.svg`. **메뉴 아이콘**: 메뉴에 저장된 아이콘 키 → 프론트 `MenuGlyph` 레지스트리.

## 실행 / 검증
- Backend: `cd backend && ./gradlew bootRun` (dev 설정 `application-dev.yml`, 운영은 env `DB_URL`·`JWT_SECRET` 등).
- Frontend: `cd frontend && npm run dev` (`http://localhost:3000`, `/api`→8080 프록시).
- 테스트: `cd backend && ./gradlew test` — 실 PostgreSQL + 실서버(RANDOM_PORT). **모킹 0**. CI: `.github/workflows/ci.yml`.
- 매퍼 XML을 고친 뒤에는 **`test`를 돌려 부팅 가능 여부를 확인**한다(`compileJava`는 XML을 검증하지 않아 이스케이프 실수를 못 잡는다).
- 파일은 **UTF-8(BOM 없음)**. 한글 경로/파일명 그대로 유지. SQL 실행 시 `PGCLIENTENCODING=UTF8`.
