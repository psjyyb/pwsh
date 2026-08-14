# 표준 웹 기본틀(framework) 규격서 — v0.3

> 목적: 여러 프로젝트의 공통 기반이 되는 **표준 틀(framework)**. 관리자 CMS(사용자·메뉴·권한·코드·게시판·파일)를 매번 다시 만들지 않는 것이 목표.
> 갱신: 2026-07-15 (JPA 도입안 폐기 → MyBatis 범용 방식으로 확정, common/global/domain 3분류 반영)

---

## 0. 설계 방향 (확정)
| 항목 | 결정 |
|---|---|
| 접근 | 검증된 방식 위주 + 최소 구성 (억지로 새 기술 도입 X, 틀은 가볍게) |
| 프레임워크 | 순수 Spring Boot (별도 프레임워크 계층 없음) |
| 영속성 | **MyBatis 단독** (JPA 미사용) — `sql_id` 범용 방식 |
| 프론트 | React + TypeScript + Vite (예정), 백엔드는 REST API |
| 인증 | JWT (Access + Refresh) |
| 네이밍 | DB=snake_case, Java/JS=camelCase (자동 매핑) |
| DB | PostgreSQL |

---

## 1. 기술 스택
| 구성 | 값 |
|---|---|
| Java | 17 (LTS) |
| Spring Boot | 4.1.x |
| 빌드 | Gradle (Wrapper 8.14) |
| 영속성 | MyBatis (`mybatis-spring-boot-starter 4.0.1`) — JPA 미사용 |
| DB | PostgreSQL 16 |
| 보안 | Spring Security 6 + JWT (`jjwt 0.12.6`) |
| 유틸 | Lombok |
| 프론트 | React + TS + Vite + AntD (예정) |

> Boot 3.5는 EOL이라 4.1 채택. MyBatis는 Initializr가 4.1 미등록이지만 4.0.1 starter가 실제 호환됨(검증 완료).

---

## 2. 프로젝트/패키지 구조

### 모노레포
```
pwsh/
├─ backend/     Spring Boot (REST API)
├─ frontend/    React (예정)
├─ sql/         schema.sql, data.sql
└─ docs/        규격/규칙/조사 문서
```

### 백엔드 패키지 (3분류)
틀(common + global)과 업무(domain)를 분리한다 — 새 프로젝트는 domain만 새로 쓰고 나머지는 그대로 가져간다.
```
com.pwsh
├─ common/                  ← 공통 재사용 (도메인이 import)
│  ├─ BaseVO, CommonDAO
│  ├─ response/   ApiResponse, ApiError
│  ├─ exception/  ErrorCode, BusinessException
│  └─ util/       StringUtil, PageUtil
├─ global/                  ← 설정·인프라 (거의 고정, web/service 규칙 미적용)
│  ├─ config/     SecurityConfig, WebConfig, AuditInterceptor, DataInitializer
│  ├─ security/   CustomUserDetails, CustomUserDetailsService, jwt/(JwtTokenProvider, JwtAuthenticationFilter)
│  ├─ exception/  GlobalExceptionHandler
│  └─ web/        ClientIpHolder, ClientIpInterceptor
└─ domain/                  ← 업무 (자주 추가, web/service 규칙)
   ├─ code/ menu/ prog/ page/ popup/ policy/ config/ user/ eventlog/   ← 표준 CRUD 완료 (eventlog=행위 감사로그, 조회 전용)
   └─ auth/  web/AuthController · service/LoginRequest,TokenResponse (로그인 전용)
```
- **domain 도메인 = web(Controller) + service(VO + 단일 `@Service`)**. 모든 도메인에 `{Domain}Service` 하나(§5 확정 — 기준: `code/CodeService`).
- 매퍼 XML: `resources/mapper/{name}/{Name}_SQL.xml` (namespace=`{name}DAO`)
- 진척/남은 도메인/환경 주의: **`docs/HANDOFF.md`** 참조.

---

## 3. 네이밍/타입 규약 (상세는 db-conventions.md)
- DB 컬럼 snake_case, Java/JS camelCase → **MyBatis `map-underscore-to-camel-case=true`** 로 자동 매핑
- VO/DTO 필드는 **String 통일** (날짜·숫자·yn 전부 String, 조회 결과도 String으로 받아 필요 시 변환)
- REST 엔드포인트: `/api/{복수명사}` (예: `/api/codes`)

---

## 4. 공통 계층 (common + global)

### BaseVO (common)
모든 도메인 VO의 부모. 페이징(`pageIndex`/`size`/`getOffset`) + 검색(`searchCondition`/`searchKeyword`/`selectedId`) + 공통키(`dbKey`) + audit(`regId`/`updId`/`regDt`/`updDt`/`regIp`/`updIp`/`useYn`).

### CommonDAO (common)
`SqlSessionTemplate`으로 `sql_id`(=`namespace.stmtId`) 문자열을 범용 실행. 도메인마다 DAO 인터페이스를 만들지 않는다.
```java
commonDAO.selectList("codeDAO.selectList", vo);
commonDAO.insert("codeDAO.insert", vo);
```

### AuditInterceptor (global/config)
MyBatis Interceptor. INSERT/UPDATE 시 BaseVO의 audit을 자동 세팅:
- `regId`/`updId` = 로그인 사용자(SecurityContext)
- `regIp`/`updIp` = 요청 IP(ClientIpHolder, 인터셉터가 저장)
→ 도메인은 audit 신경 안 씀.

### ApiResponse (common/response)
표준 응답 봉투. 성공 `{success:true, data, error:null}` / 실패 `{success:false, data:null, error:{code,message}}`.

### GlobalExceptionHandler (global/exception)
`@RestControllerAdvice`. BusinessException·인증실패·검증실패·기타를 ApiResponse로 변환.

---

## 5. 도메인 개발 패턴 ★핵심
새 도메인 추가 시 **3파일만**:
```
domain/{name}/service/{Name}VO.java     (extends BaseVO — 필드만)
domain/{name}/web/{Name}Controller.java (액션 URL + sql_id 리터럴 위임)
resources/mapper/{name}/{Name}_SQL.xml  (namespace="{name}DAO", sql_id: selectList/selectListTotCnt/selectView/insert/update/(updateordr)/delete)
```

### 컨트롤러 규약 (기준: `domain/code/CodeController` + `CodeService`)
- **관리자/사용자 경로 분리**: 관리자=`/api/adm/{name}`, 사용자(향후)=`/api/gen/{name}`. 현재 도메인은 전부 관리자(`/api/adm`).
- **`@RestController` + 클래스 `@RequestMapping("/api/adm/{name}")`**, 메서드는 **전부 `@RequestMapping`**(액션 URL, `.do`).
- **컨트롤러는 매핑만.** 업무 로직은 Service로(아래 Service 계층). try/catch 안 함 → 예외는 `GlobalExceptionHandler`가 ApiResponse+HTTP상태로 일괄 변환.
- **표준 5개 메서드 고정**: `selectList` / `selectView` / `insert` / `update` / `delete`. 변형은 **새 메서드를 만들지 말고 해당 동사의 `{path}` 분기로 흡수**한다:
  - 다건 조회 변형 → **`/select{Name}List{path}.do`** (예: `selectCodeListTree.do`=계층, `selectCodeListCombo.do`=콤보, `selectAuthgrpListMenu.do`=그룹 메뉴권한 목록)
  - 단건/계산 조회 변형 → **`/select{Name}View{path}.do`** (예: `selectCodeViewNextChild.do`=다음 코드 계산)
  - 수정/서브저장 변형 → **`/update{Name}{path}.do`** (예: `updateCodeordr.do`=정렬교환, `updateUserPassword.do`=비번, `updateAuthgrpMenu.do`=그룹-메뉴 매핑 저장)
  - **빈 path = 표준 URL**(`selectCodeList.do` 등). 단일 매핑 `/select{Name}List{path}.do` 하나가 빈값·분기값 모두 매칭(검증 완료).
  - ⚠️ **`selectCodeComboList`·`selectMainPopupList`·`saveAuthgrpMenu` 같은 별도 메서드 신설 금지** — 위 `{path}` 규칙으로 접는다.
- **특수 예외(5+{path}로 표현 불가한 것만 별도 메서드 허용)**: 파일 멀티파트 업로드/바이너리 다운로드/유지보수(`file`), 공개 이미지 서빙(`PubImageController`), 인증(`auth`: login/refresh/pw*), 싱글턴 설정(`config`: view/update), 단건뷰 없는 도메인(`comment`), 읽기전용(`eventlog`).
- **URL·sql_id는 리터럴로 작성**(grep 추적성). 요청 **JSON `@RequestBody {Name}VO`**, 응답 **`ApiResponse` 봉투**(목록=`Map.of("list","totCnt","page")`). 공통 헬퍼 `common/util`(`StringUtil.isEmpty`, `PageUtil.of`, `Validate.required`).

### Service 계층 (확정)
- `Service+ServiceImpl` 인터페이스 분리 **안 함** — **단일 `@Service` 클래스**.
- **모든 도메인에 `{Domain}Service` 하나**(로직 유무 무관 — 규칙 통일). 컨트롤러는 `CommonDAO`를 직접 호출하지 않고 **서비스에 위임**, 로직·`@Transactional`은 서비스에.
  - 도메인당 서비스는 **정확히 하나**. 용도별로 이름 다른 서비스를 여러 개 두지 않는다 → 파일 도메인은 `FileService`(CRUD·업로드·매핑·GC 통합, 구 `FileGcService` 흡수), eventlog는 `EventLogService`(감사기록 write + 조회 겸용, 여러 도메인이 공유).
  - 예외: `CustomUserDetailsService`는 Spring Security 계약(UserDetailsService) 구현체라 도메인 서비스가 아님(그대로).
- 예: `CodeService`(nextChild 계산·ordr swap·delete+shift), `AuthService`(로그인·잠금·JWT), `PopupService`(이미지 sync·삭제 cascade).
- 입력 필수값 검증(`Validate.required`)·복잡도(`PasswordPolicy`)는 컨트롤러 매핑 진입부(입력 방어는 매핑 계층 관심사).

### 도메인별 예외(성격상)
- `config`: 단일 행 → selectView/update만. `eventlog`: **행위 감사로그**(로그인+등록/수정/삭제를 EventLogAspect가 자동기록, 조회 제외) → 화면은 조회 전용, append-only(use_yn·upd 컬럼 없음). ※ `accesslog`는 폐기(2026-07-20, 로그인은 eventlog로 통합).

---

## 6. 인증·인가 (JWT + 권한그룹 RBAC)

### 인증 (JWT)
- 로그인 `POST /api/auth/login` → Access(30분)+Refresh(14일). 토큰에 `typ`(access/refresh)·`ver`(token_ver) 클레임 포함
- `JwtAuthenticationFilter`가 `Bearer` 검증 + `t_user.token_ver`와 토큰 `ver` 대조(불일치=거부)
- `/api/auth/**` permitAll, 그 외 인증 필요(STATELESS). 비밀번호 BCrypt, secret은 SHA-256 해싱해 HS256 키
- `DataInitializer`가 기동 시 admin/user 자동 생성

### 단일세션·세션 무효화 (token_ver)
- `t_user.token_ver`(정수)를 로그인 시 +1 → 발급 토큰의 `ver`와 대조 → **last-wins 단일세션**(새 로그인이 이전 기기 무효)
- **로그아웃** `POST /api/auth/logout`: token_ver +1 → 발급 토큰 즉시 무효
- **강제 로그아웃**: 관리자가 사용자관리에서 `updateUserForceLogout`
- **비번 변경·관리자 리셋**: token_ver +1 → 기존 세션 전부 무효(재로그인)

### 인가 (권한그룹 RBAC)
- 권한그룹 3종 **관리자(ADMIN)/회원(MEMBER)/비회원(GUEST)** — 비로그인=GUEST로 취급
- **메뉴 노출**: `selectMenuTree`가 실효그룹의 `t_auth` 권한으로 필터(로그인 시 메뉴 구성 자동 변경)
- **관리 API**: `PermissionInterceptor`가 `/api/adm/{domain}`을 메뉴 `link_url=/adm/{domain}` 권한으로 검사(없으면 403·fail-closed, admin 전면 허용)
- **콘텐츠 단위(B)**: `GenAccessGuard`가 게시판/페이지/댓글 조회·작성을 노출 메뉴의 그룹권한으로 판정(딥링크 차단)
- **소유자 검사**: 게시글/댓글 수정·삭제는 작성자 본인·관리자만(`SecurityUtil.assertOwnerOrAdmin`)
- **파일**: 공개이미지/다운로드는 연결 콘텐츠 접근권으로 인가(순차 id 열거 IDOR 차단), 파일관리(gc/삭제/목록)는 관리자 전용

---

## 7. DB
- 스키마: `sql/schema.sql`, 초기데이터: `sql/data.sql` — **수동 실행**(Flyway 등 미사용), UTF-8
- 규칙: `docs/db-conventions.md` (FK명=참조PK명, 물리FK 미사용, audit 컬럼, yn=varchar(1), PK=IDENTITY 등)
- 공통 기초 테이블: t_user·t_menu·t_auth·t_auth_grp·t_bbsinfo·t_bbs·t_comment·t_file·r_file·t_config·t_event_log 등 (화면 목록은 메뉴 `conn_ty`로 관리 — 별도 프로그램 테이블 없음)
- **관리자 UI/브랜딩**: `t_menu.icon`(메뉴별 아이콘 키 → 프론트 `MenuGlyph` 레지스트리 렌더). 로고는 `r_file`(map_key=config_id, file_loc='LOGO')로 **환경설정에서 업로드**(미설정 시 프론트 기본 로고 `assets/logo.svg`). 로고는 공개 서빙(로그인 화면 노출).

---

## 8. 빌드/실행
```powershell
# DB: schema.sql + data.sql 실행 (PGCLIENTENCODING=UTF8)
# backend
cd backend
$env:JAVA_HOME='...jdk-17'   # gradle.properties에 org.gradle.java.home 고정됨
./gradlew bootRun
```
- 접속정보: `application-dev.yml` (운영은 환경변수 `DB_URL`·`JWT_SECRET` 등)

### 테스트 / CI
- 테스트: `@SpringBootTest(RANDOM_PORT)` 실서버 + 실 PostgreSQL(프로파일 `test`, `test-reset.sql`→`schema.sql`→`data.sql` 자동 로드) + JDK HttpClient. **모킹 0**. jacoco 커버리지. 실행 `./gradlew test`
- CI: `.github/workflows/ci.yml` — main push/PR 시 백엔드 테스트(PostgreSQL 16 서비스 컨테이너) + 프론트 빌드·타입체크 자동 실행

---

## 9. 재사용 방식 (틀 → 프로젝트)
- **지금 단계**: framework를 **시작 템플릿으로 복사** → 새 프로젝트 → `domain`에 업무 추가. common/global(틀 코어)은 물려받음.
- **추후**: 프로젝트가 여러 개면 코어(common/global)를 **공통 라이브러리(jar)** 로 분리해 여러 프로젝트가 공유 가능.

---

## 10. 남은 보완 (추후)
- 테스트 커버리지 확대(현재 ~50% → 목표 90%): 도메인 CRUD, 프론트 vitest
- 게시판 Phase2: 분류(카테고리)·게시판 스킨 (답글은 완료 — p_bbs_id 스레드)
- 인덱스·성능 점검(자주 도는 쿼리), 요청/에러 로깅
- (완료) 응답 NON_NULL·401 EntryPoint·권한(t_auth RBAC)·콘텐츠/파일 접근제어·단일세션(token_ver)·파일 업로드·프론트·테스트/CI·메뉴 아이콘·로고(브랜딩)
