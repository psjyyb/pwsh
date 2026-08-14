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
└─ sql/        DB 스크립트 (schema.sql, data.sql)
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

## 설계 요약
- **컨트롤러는 매핑만**(도메인당 5메서드 고정), 로직은 도메인당 단일 `@Service`
- API 응답은 `ApiResponse{success,data,error}` 표준, 목록은 `{list,totCnt,page}`
- **RBAC 3계층(관리자/회원/비회원) + JWT 단일세션** — 로그아웃·비번변경 시 기존 토큰 즉시 무효화
- 회원 식별은 **공개 식별자(handle)**만 클라이언트에 노출, 로그인 ID는 서버에만 둔다
- 삭제는 논리삭제, 파일은 매핑을 통해서만 연결하고 고아 파일은 스케줄러가 회수
- 실시간(쪽지·모임 대화)은 SSE 푸시 + 폴링 폴백
- 테스트는 실 PostgreSQL 통합테스트(모킹 0) + GitHub Actions CI

## 화면 추가 방식 (동적 매핑)
메뉴 메타데이터가 단일 소스라 **하드코딩된 화면 레지스트리가 없다.**

- **관리자 기능화면**: `frontend/src/adm/{domain}/{이름}Page.tsx` 파일 1개를 규칙대로 만들고 메뉴만 등록하면 사이드바·탭·라우팅·렌더가 전부 자동(`import.meta.glob`으로 빌드 시 수집).
- **콘텐츠 페이지**: 관리 화면에서 페이지를 작성하고 메뉴에 연결하면 끝(코드 작성 없음). 게시판도 같은 방식.

자세한 개발 규약은 [CLAUDE.md](CLAUDE.md) 참조.
