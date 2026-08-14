# pwsh (People Who Share Hobbies)

취미 커뮤니티 — 취미별(등산·보드게임·낚시…) 게시판에서 정보를 공유·토론하고, 함께할 사람을 **모집**하는 웹 서비스. 직접 만든 표준 웹 기본틀(framework) 위에 얹은 첫 서비스다.

주요 도메인: **취미 게시판**(카테고리 = 게시판) · **모집**(모임원 모집·참여신청·수락) · **셀프 회원가입**(닉네임). 사용자·메뉴·권한·코드·게시판·파일 등 공통 CMS 기능은 표준 틀에 이미 들어 있다.

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
- Node 20 (프론트) — TLS를 가로채는 보안 프로그램을 쓰면 CA 지정 필요(`NODE_EXTRA_CA_CERTS`)
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
# TLS 가로채기(백신 등) 환경이면 최초 npm 설치/실행 시 CA 지정
$env:NODE_EXTRA_CA_CERTS='<ca.pem 경로>'
npm install
npm run dev        # http://localhost:3000 (백엔드 8080 필요, /api 프록시)
```

## 규약 요약
- DB 컬럼은 `snake_case`, Java/JS는 `camelCase` (자동 매핑)
- FK 컬럼명 = 참조 대상 PK명 그대로, 물리 FK 제약 미사용
- **컨트롤러=매핑만(5메서드+`{path}`), 로직은 도메인당 단일 `@Service`**
- **RBAC 3계층(관리자/회원/비회원) + 단일세션(`token_ver`)**, 인증 JWT
- API 응답은 `ApiResponse{success,data,error}` 표준
- 테스트는 실 PostgreSQL 통합테스트(모킹 0) + GitHub Actions CI

### 1) 관리자 기능화면 (고유 CRUD 로직)
파일을 규칙대로 만들고 **메뉴만 등록**하면 사이드바·탭·라우팅·렌더가 전부 자동. (등록 코드 수정 불필요)

- **파일 규칙**: `frontend/src/adm/{domain}/{이름}Page.tsx`
  - 폴더명 `{domain}` = 메뉴 `link_url`의 마지막 세그먼트 (`/adm/{domain}`)
  - 파일명은 **`~Page.tsx`로 끝** (도메인 폴더당 **정확히 1개**), `export default` 컴포넌트
  - 하위 컴포넌트는 `~Modal.tsx` 등 다른 접미어 사용(수집 대상 아님)
  - 예: `/adm/board` → `src/adm/board/BoardListPage.tsx`
- **메뉴 등록**: 메뉴관리에서 연결유형=`URL`, `link_url=/adm/{domain}`, 메뉴명 입력
- **동작 원리**: `admScreens.tsx`가 `import.meta.glob('./*/*Page.tsx')`로 화면을 빌드시 자동 수집 → `resolveScreen('/adm/{domain}')`가 컴포넌트를 찾아 렌더. 슈퍼관리자(MEM02)는 자동 노출, 그 외 그룹은 권한그룹관리에서 권한 부여.

### 정리
| 구분 | 필요한 작업 |
|---|---|
| 관리자 기능화면 | 파일 1개(규칙) + 메뉴 등록 |
| 콘텐츠 페이지 | 메뉴 등록만 (일반페이지관리에서 콘텐츠 작성) |
