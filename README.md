# pwsh (People Who Share Hobbies)

취미 커뮤니티 — 취미별(등산·보드게임·낚시…) 게시판에서 정보를 공유·토론하고, 함께할 사람을 **모집**하는 웹 서비스. 표준 웹 기본틀을 기반으로 개발한다.

주요 도메인: **취미 게시판**(카테고리 = 게시판) · **모집**(모임원 모집·참여신청·수락) · **셀프 회원가입**(닉네임). 기반 틀의 사용자·메뉴·권한·코드·게시판·파일 구조를 그대로 계승한다.

## 기술 스택
- **Backend**: Spring Boot 4.1 · Java 17 · Gradle · MyBatis · Spring Security + JWT
- **Frontend**: React 18 · TypeScript · Vite · Ant Design · axios · React Router
- **DB**: PostgreSQL

## 디렉터리 구조
```
pwsh/
├─ backend/    Spring Boot REST API (MyBatis, JWT)
├─ frontend/   React SPA (adm=관리자 / gen=사용자 영역)
├─ sql/        DB 스크립트 (schema.sql, data.sql)
└─ docs/       규격·규칙·조사 문서
```

## 개발 환경
- Java 17 (`JAVA_HOME` 지정 필요)
- Node 20 (프론트) — npm은 보안 프로그램 CA 신뢰 필요(`NODE_EXTRA_CA_CERTS`)
- PostgreSQL (DB명: `pwsh`)
- Gradle Wrapper 포함 (별도 설치 불필요)

## 실행 방법
### 1. DB 준비
```powershell
# DB 생성
psql -U postgres -c "CREATE DATABASE pwsh ENCODING 'UTF8';"
# 스키마 + 초기데이터 (UTF-8 필수)
$env:PGCLIENTENCODING='UTF8'
psql -U postgres -d pwsh -f sql/schema.sql
psql -U postgres -d pwsh -f sql/data.sql
```
### 2. Backend 실행
```powershell
cd backend
./gradlew bootRun
```
- DB 접속정보는 `backend/src/main/resources/application-dev.yml`
- 운영 접속정보/시크릿은 환경변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`)로 주입

### 3. Frontend 실행
```powershell
cd frontend
# 로컬 환경 TLS 신뢰: 최초 npm 설치/실행 시 CA 지정
$env:NODE_EXTRA_CA_CERTS='<ca.pem 경로>'
npm install
npm run dev        # http://localhost:3000 (백엔드 8080 필요, /api 프록시)
```

## 문서
> **개발 시작점: 루트 [CLAUDE.md](CLAUDE.md)** — 개발 규약·새 도메인 추가법·인증/파일 모델 요약(다른 세션/개발자 참고용).

| 문서 | 내용 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | **개발 가이드(규약·새 도메인 추가·인증/파일)** — 시작점 |
| [docs/standard-template-spec.md](docs/standard-template-spec.md) | 표준 기본틀 규격서(상세) |
| [docs/db-conventions.md](docs/db-conventions.md) | DB/테이블 설계 규칙 |
| [docs/db-conventions.md](docs/db-conventions.md) | DB/테이블 설계 규칙 |
| [docs/file-orphan-gc.md](docs/file-orphan-gc.md) | 파일 참조 모델·고아파일 정리(GC)·삭제 복구 SQL |

## 규약 요약
- DB 컬럼은 `snake_case`, Java/JS는 `camelCase` (자동 매핑)
- FK 컬럼명 = 참조 대상 PK명 그대로, 물리 FK 제약 미사용
- 공통 audit 컬럼(`reg_*`/`upd_*`) + 논리삭제(`use_yn`)
- **컨트롤러=매핑만(5메서드+`{path}`), 로직은 도메인당 단일 `@Service`**
- **RBAC 3계층(관리자/회원/비회원) + 단일세션(`token_ver`)**, 인증 JWT
- **파일 참조는 `r_file` 매핑만**(직접 file_id 컬럼 미사용)
- API 응답은 `ApiResponse{success,data,error}` 표준
- 테스트는 실 PostgreSQL 통합테스트(모킹 0) + GitHub Actions CI
- (상세 규약·새 도메인 추가 절차는 [CLAUDE.md](CLAUDE.md))

## 화면·메뉴 규약 (동적 매핑)
메뉴/화면 메타데이터는 **`t_menu`(DB)가 단일 소스**다. 하드코딩 화면 레지스트리는 없다.
메뉴명·노출·순서·계층·탭 제목·경로는 모두 `t_menu`에서 오고, 화면 컴포넌트만 아래 파일 규칙으로 자동 연결된다.

### 1) 관리자 기능화면 (고유 CRUD 로직)
파일을 규칙대로 만들고 **메뉴만 등록**하면 사이드바·탭·라우팅·렌더가 전부 자동. (등록 코드 수정 불필요)

- **파일 규칙**: `frontend/src/adm/{domain}/{이름}Page.tsx`
  - 폴더명 `{domain}` = 메뉴 `link_url`의 마지막 세그먼트 (`/adm/{domain}`)
  - 파일명은 **`~Page.tsx`로 끝** (도메인 폴더당 **정확히 1개**), `export default` 컴포넌트
  - 하위 컴포넌트는 `~Modal.tsx` 등 다른 접미어 사용(수집 대상 아님)
  - 예: `/adm/board` → `src/adm/board/BoardListPage.tsx`
- **메뉴 등록**: 메뉴관리에서 연결유형=`URL`, `link_url=/adm/{domain}`, 메뉴명 입력
- **동작 원리**: `admScreens.tsx`가 `import.meta.glob('./*/*Page.tsx')`로 화면을 빌드시 자동 수집 → `resolveScreen('/adm/{domain}')`가 컴포넌트를 찾아 렌더. 슈퍼관리자(MEM02)는 자동 노출, 그 외 그룹은 권한그룹관리에서 권한 부여.

### 2) 사용자(gen) 콘텐츠 페이지 (코드 불필요)
- 일반페이지관리에서 페이지 작성(제목+본문 HTML) → `page_id` 생성
- 메뉴관리에서 영역=`사용자`, 연결유형=`페이지`, 페이지ID=`page_id` 로 등록
- `/gen` 상단 메뉴 클릭 시 범용 `GenPageView`가 `conn_id(page_id)`로 `t_page`를 조회해 렌더. **파일 작성 없이** 노출.
- 게시판(연결유형=`게시판`)은 bbs 모듈 구축 후 동일 방식.

### 정리
| 구분 | 필요한 작업 |
|---|---|
| 관리자 기능화면 | 파일 1개(규칙) + 메뉴 등록 |
| 콘텐츠 페이지 | 메뉴 등록만 (일반페이지관리에서 콘텐츠 작성) |
