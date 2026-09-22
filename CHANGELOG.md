# CHANGELOG — pwsh

pwsh는 **직접 만든 CMS(`framework` 저장소)를 복사해 만든 파생 서비스**다.
그래서 버전이 두 개다.

| 값 | 뜻 | 적는 곳 |
|---|---|---|
| `version` | pwsh 자체 버전(모집·취미·채팅 등 이 서비스 고유 기능) | `backend/build.gradle` · `frontend/package.json` |
| `cmsVersion` | **바탕 CMS의 어느 버전까지 흡수했는지** | `backend/build.gradle`의 `ext.cmsVersion` |

둘은 따로 움직인다 — pwsh 기능만 추가하면 `version`만, CMS를 따라가면 `cmsVersion`만 오른다.
버전은 `X.Y.Z` 세 자리, 기능 묶음마다 Z를 올린다.

- 실행 중인 서버 확인: `POST /api/pub/version` → `{version, cmsVersion, buildTime}`
  또는 관리자 화면 사이드바 하단(`v0.6.9 · CMS 1.2.8` 형태로 표시).

## CMS를 따라잡는 방법

1. 사이드바 하단에서 현재 `CMS x.y.z`를 확인한다.
2. **framework 저장소의 `CHANGELOG.md`** 에서 그 다음 버전부터 차례로 적용한다(DDL이 누적이라 건너뛰지 않는다).
3. 각 버전의 ⚠ 표시를 반드시 확인한다 — 그대로 옮기면 깨지는 부분이 적혀 있다.
4. 다 옮겼으면 `backend/build.gradle`의 `ext.cmsVersion`을 올리고 이 파일에 기록한다.

---

## 0.6.9 (CMS 1.2.8) — 회원 라이프사이클 구멍 메움 흡수

CMS 1.2.8을 흡수했다. **배치는 맞는데 사람이 손으로 하는 경로가 어긋나던** 곳들이다.
둘 다 화면에 표시가 없어 조용히 틀린다.

- **상세 폼의 계정상태로 휴면을 드나드는 경로**가 라이프사이클 필드를 안 건드렸다.
  - 휴면 → 정상: `last_login_dt`를 안 당겨 **다음 배치에서 곧바로 재휴면**. 이제 `updateInfo`가
    `dormant_dt`를 비우고 `last_login_dt`를 당긴다(해제버튼과 동일).
  - 정상 → 휴면: `dormant_dt`도 안 찍히고 **세션도 안 끊겨** "휴면인데 로그인된 계정"이 됐다.
    이제 배치와 똑같이 `invalidateToken(DORMANT)`을 탄다.
  - ★ SET 절 오른쪽의 `status_cd`는 PostgreSQL 규칙상 **갱신 전 값**이라 "직전 상태"로 판정된다.
  - 직전 상태 조회는 `selectStatusCd`를 새로 뒀다 — `selectByMemberId`는 개인정보를 복호화해서
    그걸 쓰면 **정보수정 한 번마다 개인정보 접근로그가 쌓인다.**
- 파기 시 세션 종료 사유를 `WITHDRAW` → **`DESTROY`**(신규)로 바꿨다. 접속이력에서 셀프 탈퇴와
  개인정보 파기가 구분된다. 프론트 `END_REASON_LABEL`에도 `개인정보 파기`를 넣었다.
- **접속이력에 영문 코드가 그대로 나오던 것을 고쳤다.** `END_REASON_LABEL`에 이 저장소 고유 사유
  `WITHDRAW`(회원 탈퇴)·`SUSPEND`(관리자 계정정지)가 빠져 있었다 — 상수는 8개인데 라벨은 6개였다.
  없는 코드는 에러가 아니라 `?? r.endReason` 폴백으로 **영문 코드가 조용히 노출된다.**
  주석이 가리키던 `LoginSessionService.END_*`도 실재하지 않는 경로라 `SessionEndReason`으로 바로잡았다.
- CMS 1.2.8의 "즉시 파기가 계정을 살려 뒀다"는 **이 저장소엔 이미 없던 문제다** — 0.6.8에서
  `ux_member_nickname`(부분 유니크) 때문에 `updateDestroy`가 처음부터 `use_yn='N'`을 내리고 있었다.

### ⚠ 이 저장소에만 있는 추가 구멍 (CMS에는 없다)

제재 토글(`updateStatus`, 정지/해제)로도 **휴면에서 빠져나올 수 있다.** 휴면 계정에 해제를 걸면
STATUS01이 되는데 `last_login_dt`를 안 당겨서 역시 곧바로 재휴면됐다. CMS에는 이 경로 자체가 없다
(거기선 상태 변경이 폼 하나뿐). `updateStatus`도 같이 고쳤다 — 이 경로로는 STATUS04가 될 수 없으므로
`dormant_dt`는 무조건 비운다.

### 테스트

`MemberLifecycleTest` 10 → **14**. 추가분: 파기 시 세션 사유가 `DESTROY`인지, 폼으로 휴면 해제 후
재휴면 안 되는지, 폼으로 휴면 전환 시 세션이 끊기는지, **제재 해제로 휴면을 풀어도 재휴면 안 되는지**.
**mutation 검증 3건**: `updateStatus`의 접속일 갱신 제거 → 1건 실패 / 파기 사유를 `WITHDRAW`로 되돌림
→ 1건 실패 / `updateInfo`의 접속일 갱신 제거 → 1건 실패(전부 확인 완료). 전체 **156개 통과**.

**DB** — 마이그레이션 없음(스키마 변경이 아니라 SQL 로직 수정이다).

---

## 0.6.8 (CMS 1.2.7) — 회원 라이프사이클 (휴면 전환 · 개인정보 파기) 흡수

CMS 1.2.7을 흡수했다. 탈퇴가 `use_yn='N'`(논리삭제)뿐이라 **개인정보가 영구히 남아 있었고**,
장기 미접속 계정도 정상 상태로 계속 살아 있었다. 둘 다 배치로 정리한다.

- 계정상태 **`STATUS04 휴면`** 추가. `last_login_dt`(NULL이면 `reg_dt`) 기준 `config.dormant_days` 경과 시 전환.
- 전환 즉시 토큰·세션을 끊는다(`invalidateToken`, 사유 `DORMANT`). 휴면 계정은 로그인·토큰 재발급 모두 차단.
- ★ 해제 시 `last_login_dt`를 지금으로 당긴다 — 안 당기면 다음 배치에서 곧바로 재휴면된다.
- 전환 `dormant_notify_days`일 전 안내메일(`DORMANT_NOTICE`). 대상은 **하루치만** 뽑는다.
- 탈퇴 시 `withdraw_dt`를 찍고, `destroy_days` 경과 시 개인정보 컬럼을 비운다.
  **본인 탈퇴(`AuthService.withdraw`)와 관리자 삭제가 같은 `memberDAO.delete`를 쓰므로 양쪽 다 찍힌다.**
- 배치 순서는 **안내 → 전환 → 파기**(`member.lifecycle.cron`, 기본 매일 03:00).
- 설정: `config.dormant_days`(365) · `dormant_notify_days`(30) · `destroy_days`(30). 0이면 그 기능을 끈다.
  안내메일 링크는 `site.login-url`(운영은 env `SITE_LOGIN_URL`).

### ⚠ CMS와 다르게 옮긴 곳 (이 서비스 사정)

CMS 원문 그대로 옮기면 깨지는 부분이 셋 있었다.

1. **`nickname`은 NULL이 아니라 `'탈퇴한 회원'`으로 바꾼다.**
   CMS는 파기할 때 nickname을 비우지만, 이 서비스는 게시글·댓글·모집의 작성자를
   `(SELECT u.nickname FROM member u WHERE u.member_id = X.reg_id)`로만 표기한다 —
   비우면 **과거 글의 작성자가 전부 빈칸**이 된다. 사용자가 고른 문자열은 사라지므로 파기 목적은 지켜진다.
2. **파기하면 `use_yn='N'`까지 내린다.**
   `'탈퇴한 회원'`이 둘 이상 생기는데 `ux_member_nickname`이 `use_yn='Y'`에만 걸린 부분 유니크
   인덱스라, 계정을 살려 두면 **두 번째 즉시 파기가 유니크 위반으로 터진다.**
   (CMS에는 nickname 유니크 인덱스가 없어 드러나지 않는 차이다.) 개인정보가 사라진 계정을
   살려 둘 이유도 없으므로 `destroyNow`는 세션도 함께 끊는다.
3. **`profile_file_id`도 NULL로 비운다** — 얼굴 사진도 개인정보다.
   참조가 끊기면 기존 고아 파일 GC(A 케이스)가 알아서 회수한다(별도 삭제 코드 불필요).
4. `MEMBER_RESTORE` 코드는 **이미 있다**(관리자 정지해제). 휴면 해제도 같은 "정상으로 되돌린다"
   행위라 새로 넣지 않고 이름만 `정지·휴면 해제`로 넓혔다 — 새로 INSERT 하면 PK 중복으로 기동이 막힌다.
5. `ALTER TABLE member ALTER COLUMN name DROP NOT NULL`은 이 저장소에선 **no-op**이다
   (셀프가입 때문에 이미 NULL 허용). CMS와 diff를 뜰 때 빠진 줄로 보이지 않도록 그대로 남겼다.

### 테스트

`MemberLifecycleTest`(10) — CMS의 9개 + **'탈퇴한 회원' 중복 파기** 1개.
**mutation 검증**: nickname을 NULL로 → 2건 실패, `use_yn='N'`을 빼면 → 1건 실패(확인 완료).
전체 **152개 통과**.

**DB** — `V3__member_lifecycle.sql`(CMS 대역). **실행할 SQL이 없다** — 앱을 띄우면 Flyway가 적용한다.

## 0.6.7 (CMS 1.2.6) — DB 마이그레이션(Flyway) 흡수

CMS 1.2.6을 흡수했다. **앱이 뜨면서 `db/migration`의 미적용 마이그레이션만 자동 실행**하고
적용 이력을 `flyway_schema_history`에 남긴다. 더 이상 dev·운영 DB에 DDL을 손으로 넣지 않는다.

- `V1__baseline_schema.sql`(1,158줄) · `V2__baseline_data.sql`(461줄) — 도입 시점 스냅샷.
  **CMS 테이블과 pwsh 고유 테이블이 모두 들어 있다.**
- test 프로파일은 `spring.sql.init`을 끄고 Flyway `clean → migrate`로 바꿨다(`TestFlywayConfig`).
  전체 테스트가 매번 실제 마이그레이션을 실행하므로 마이그레이션이 깨지면 여기서 먼저 드러난다.
- `sql/schema.sql`·`sql/data.sql`은 더 이상 실행되지 않는다(참고용 스냅샷 표시를 달았다).
  `backend/src/test/resources/test-reset.sql`도 쓰이지 않는다.

### ★ 번호 규칙 — 파생 프로젝트의 핵심

CMS와 pwsh가 같은 번호를 쓰면 충돌한다. 대역을 나눈다.

| 대역 | 용도 |
|---|---|
| `V1`·`V2` | **이 저장소의 베이스라인** (CMS+pwsh 전체) |
| `V3` ~ `V999` | CMS에서 가져오는 마이그레이션 |
| `V1001` ~ | pwsh 고유 기능 |

⚠ **CMS의 `V1`·`V2`(베이스라인)는 가져오지 않는다** — 이 저장소의 V1·V2가 이미 그 시점을 포함한다.
CMS는 **V3부터** 복사한다.
⚠ 베이스라인보다 낮은 번호를 쓰면 `baseline-version: 2` 때문에 **조용히 건너뛴다**(실행되지 않는다).

### ⚠ 빌드 의존성 두 개 다 필요

`spring-boot-flyway`(Boot 4는 자동설정이 모듈로 분리) + `flyway-database-postgresql`(Flyway 10부터
DB 지원 분리). core만 넣으면 **에러 없이 마이그레이션이 실행되지 않는다.**

### 검증

| | 결과 |
|---|---|
| pwsh_test | `clean` → V1(1,118ms) + V2(79ms), **142개 테스트 0 실패** (89초) |
| pwsh(dev) | `Successfully baselined schema with version: 2` → `No migration necessary`, 데이터 그대로(member 2 / recruit 17 / hobby 15) |

**DB** — 별도 실행할 SQL 없음. 앱을 띄우면 baseline이 잡힌다.

---

## 0.6.6 (CMS 1.2.5) — 폼(신청·민원·설문) 흡수

CMS 1.2.5를 그대로 흡수했다(수정 없음). 신청서·민원접수·설문을 한 엔진으로 만들고, 문항을 DB에
정의해 사용자 화면을 자동 생성한다. 관리자 **폼 관리 > 폼 설정 / 응답 관리**.

- 테이블 4개(`form`·`form_field`·`form_answer`·`form_answer_value`), 문항 유형 8종,
  문항별 집계 + CSV 내려받기.
- 사용자 노출은 **메뉴 연결유형 `MENU05`(폼)** + `conn_id=form_id` → `/gen/form/{form_id}`.
  `GenLayout.targetOf`에 분기를 넣고 `genScreens`에 `/gen/form/:formId`를 등록했다.
  메뉴관리의 연결유형에 '폼' 선택이 생겼다(드롭다운으로 폼을 고른다).
- `SecurityConfig` permitAll + `PermissionInterceptor.EXEMPT_SUFFIX`에
  `selectFormView.do`·`insertFormAnswer.do`를 **둘 다** 넣었다(비로그인 제출 허용 폼 때문).

### 이 서비스에서 쓸 만한 자리

취미 모임 신청서(오프라인 행사 참가 신청), 운영 문의·신고 외 민원 접수, 모임 후 만족도 설문 등이
코드 수정 없이 만들어진다. 개인정보 문항(`privacy_yn='Y'`)은 답이 암호화 저장되고 **조회가
개인정보 접근로그(0.6.5)에 남는다** — 참가 신청서에 연락처를 받는 경우가 여기 해당한다.

⚠ 폼은 **메뉴에 걸어야 사용자가 열 수 있다**(`GenAccessGuard.checkForm`이 메뉴 권한으로 판정).
만들어 놓고 메뉴를 안 만들면 관리자만 보인다 — 의도된 동작이지만 처음엔 "왜 안 보이지"가 된다.

**테스트**: `FormTest`(12, CMS와 동일). 전체 **142개 통과**.

**DB** — framework 저장소 CHANGELOG 1.2.5의 DDL을 그대로 적용한다. 이 저장소는 코드 그룹
정렬번호가 16~18이고, 관리자 메뉴는 `폼 관리`(menu_id 56) 아래 `폼 설정`(57) · `응답 관리`(58)다.

---

## 0.6.5 (CMS 1.2.4) — 개인정보 접근 로그 흡수

CMS 1.2.4를 그대로 흡수했다(수정 없음). 개인정보를 복호화해 읽은 요청이 `privacy_log`에
요청 1건 = 1행으로 자동 기록된다. 로그관리 > **개인정보 접근로그**에서 본다.

- `global/log/`(수집기 + MyBatis 탐지 인터셉터 + 요청종료 적재 인터셉터) · `domain/privacylog/` ·
  매퍼 · 관리화면. `WebConfig`에 `privacyLogFlushInterceptor` 등록(★ `clientIpInterceptor`보다 뒤).
- 실행 SQL에 `DECRYPT(`가 있으면 개인정보 조회로 본다 — 선언이 아니라 탐지라, 취미·모집처럼
  이 서비스가 나중에 추가한 도메인에서 개인정보를 읽어도 자동으로 잡힌다.

### 이 서비스에서 실제로 기록되는 곳

복호화 조회는 세 군데뿐이고 **전부 관리자 화면**이다 — 사용자(gen) 화면은 공개 식별자 규칙 덕에
개인정보를 읽지 않으므로 기록이 사용자 트래픽으로 불어나지 않는다.

| 매퍼 | 화면 |
|---|---|
| `memberDAO` | 회원관리 > 사용자관리(목록·상세·이름 검색) |
| `loginSessionDAO` | 회원관리 > 접속세션(표시 이름을 복호화) |
| `mailLogDAO` | 로그관리 > 메일발송이력(수신자 주소 복호화) |

⚠ **`email_verification.target`은 가입 인증 동안 이메일을 평문으로 들고 있다**(TTL 5분, 검증 성공 시 삭제).
암호화 컬럼이 아니라 이 기록에는 잡히지 않는다 — 조회하는 관리 화면이 없어 지금은 문제가 아니지만,
나중에 그 값을 보여주는 화면을 만들면 접근기록에서 빠진다는 점을 알고 있어야 한다.

**테스트**: `PrivacyLogTest`(8, CMS와 동일). 전체 **130개 통과**.

**DB** — framework 저장소 CHANGELOG 1.2.4의 DDL을 그대로 적용한다(메뉴 부모 = 로그관리 `menu_id` 10).

---

## 0.6.4 (CMS 1.2.3) — 메일 템플릿·발송 이력 흡수 + 인증 메일을 그 위로 이전

CMS 1.2.3의 메일 기반(`MailService` + `mail_template` + `mail_log`)을 흡수하고,
**이미 있던 인증 메일 발송을 그 위로 옮겼다**. 옮기지 않으면 이 서비스에서 실제로 나가는
유일한 메일이 이력에 안 남는다.

### 흡수한 것

- `domain/mail/`(VO 2 · `MailService` · 컨트롤러 2) · 매퍼 2 · 관리화면 2
  (시스템관리 > **메일템플릿**, 로그관리 > **메일발송이력**).
- `mail.enabled`(기본 false) · `mail.log.retention-days`(90일) 설정 추가.
- 발송 결과 공통코드 `MAIL00`(SUCCESS/FAIL/SKIP).

### 인증 메일 이전 (`EmailVerifyService`)

- `JavaMailSender`를 직접 부르던 코드와 **자바 상수로 박혀 있던 HTML 본문(약 30줄)을 걷어내고**
  `mailService.send("SIGNUP_CODE" | "RESET_CODE", 수신자, {code, ttl})` 한 줄로 바꿨다.
  디자인은 그대로 옮겨 `mail_template` 시드 2건이 됐다 — 이제 문구·디자인을 관리화면에서 고친다.
- 발송 실패는 **예외를 던져 코드 발급까지 롤백**한다. 사용자가 받지 못한 코드를 유효한 것처럼
  남겨두면 "인증번호가 안 온다"가 계속 반복된다. (이력은 독립 트랜잭션이라 롤백돼도 남는다.)
- `error.email.notConfigured` 메시지는 쓰는 곳이 없어져 제거했다(미설정은 `MailService`가
  `FAIL` 이력으로 처리한다).

### pwsh 고유 주의

⚠ 코드 `SUCCESS`/`FAIL`/`SKIP`은 `code.code_id`가 전역 유일이라 기존 코드와 겹치면 안 된다
(현재 충돌 없음 — `APPLY00`·`RECRUIT00` 등과 값이 다르다).

**테스트**: `MailTest`(10) — CMS 9건 + **가입 인증 메일이 템플릿·이력을 탄다**(제목이 시드
템플릿과 일치하고 `{{code}}`·`{{ttl}}`이 실제 값으로 치환됐는지). 전체 **122개 통과**.

**DB** — framework 저장소 CHANGELOG 1.2.3의 DDL을 그대로 적용하고, 템플릿 시드는
이 저장소 `sql/data.sql`의 `SIGNUP_CODE`·`RESET_CODE` 2건을 쓴다(CMS의 WELCOME/NOTICE 대신).
메뉴는 `메일템플릿`(시스템관리, `mail`) · `메일발송이력`(로그관리, `send`).

---

## 0.6.3 (CMS 1.2.2) — 배너 관리 흡수(메인 히어로 DB화) + 모바일 레이아웃 보정

### 배너 관리

메인 첫 화면의 슬라이드 3장이 `HeroSection.tsx`의 **코드 상수**였다. 문구 한 줄 고치는 데
빌드·배포가 필요했고, 실서비스로 갈수록 그게 병목이 된다. CMS 1.2.2의 `banner` 도메인을
그대로 흡수하고, 기존 문구를 그대로 초기 배너 3건으로 옮겼다(보이는 화면은 그대로다).

- 백엔드 `domain/banner/` · 매퍼 `mapper/banner/Banner_SQL.xml` · 관리화면 `adm/banner/`.
  관리자 > 시스템관리 > **배너관리**에서 제목·설명·버튼·배경이미지·노출기간·순서를 관리한다.
- 제목의 `[[...]]`=강조(그라디언트), 줄바꿈=그대로 표시. **HTML이 아니라 마커다** —
  저장값을 HTML로 렌더하면 배너 등록 권한이 곧 XSS 권한이 된다.
- 공개 경로 `/api/adm/banner/selectBannerListMain.do`를 `SecurityConfig` permitAll과
  `PermissionInterceptor.EXEMPT_SUFFIX`에 **둘 다** 넣었다. `FileService.assertServable`도
  `BANNER` 매핑을 공개로 취급한다(아니면 배경 이미지만 403).

**pwsh 고유 처리 두 가지** (CMS 원본에는 없다)

- 버튼 주소가 `#id`면 라우팅 대신 메인 안의 그 섹션으로 스크롤한다(`#collection`·`#recruit`).
  기존 CTA 동작을 그대로 유지하려고 DB 값으로 표현할 수 있게 둔 것이다.
- **로그인 상태에서는 `/signup` 버튼을 '내 피드 보기'(`/gen/feed`)로 바꿔 그린다.**
  이미 가입한 사람에게 회원가입 버튼은 의미가 없는데, 이 분기는 배너 내용으로 표현할 수 없다 →
  `HeroSection`에서만 처리한다(주석으로 이유를 남겼다).

**테스트**: `BannerTest`(6) — 게스트 조회 허용/관리 목록 401, 기간 지난·예정 배너 제외,
순서 자동부여 + UP/DOWN 교환, 수정·삭제, 제목 필수, 삭제 시 배경 이미지 `use_yn='N'` 전파.
`GuestPublicPageTest`·`AdminReadPathsTest`에 배너 엔드포인트 추가. 전체 **112개 통과**.

**DB** — `sql/schema.sql`의 `banner` 테이블 생성 + 관리자 메뉴(`/adm/banner`, 아이콘 `image`) 등록.
`sql/data.sql`에 초기 배너 3건 시드. 자세한 DDL은 framework 저장소 CHANGELOG 1.2.2 참고.

### 모바일 레이아웃 보정

- 큰 제목의 한국어가 글자 단위로 끊겨 마지막 한 글자만 다음 줄로 떨어졌다 →
  `.gen-hero-txt h2`·`.gen-sec-title`에 `word-break: keep-all`(어절 단위).
- 좁은 화면에서 헤드 밴드가 과하게 높았다 → `.gen-page-head` 최소높이·패딩 축소.

---

## 0.6.2 (CMS 1.2.1) — 헤더 내비 가운데 정렬 + 아이콘 피커 흡수

### 헤더 — 상단 메뉴가 왼쪽으로 치우쳐 있던 문제
가운데 열에 내비를 두고도 **내비 중심이 화면 중심에서 200px 넘게 왼쪽**이었다(실측).
좌·우 열 폭이 크게 달라서다 — 우측에 검색창(150) + 버튼 4개가 늘어서 폭이 443px였고,
좌측 로고는 51px였다. 이 상태로는 `1fr auto 1fr`로 두면 내비와 우측이 **겹치고**(159px),
`auto 1fr auto`로 두면 내비가 왼쪽으로 밀린다 — 우측을 줄이지 않으면 산술적으로 중앙이 안 된다.

- 검색창 → **검색 아이콘 버튼**, 마이페이지·관리자 페이지·로그아웃 → **계정 드롭다운('내 계정')**.
  우측 폭 443 → 229px.
- 그리드를 `minmax(0,1fr) auto minmax(0,1fr)`로 되돌렸다 → 내비 중심이 화면 중심과 **7px 차이**
  (좌우 여백 차이에서 오는 값), 좌우 여백 337·159px로 겹침 없음.

### 아이콘 피커(CMS 1.2.1) 흡수
- `IconPicker` + 확장된 `MenuGlyph`(45 → 72개)를 그대로 가져왔다. 메뉴관리의 아이콘 선택이
  드롭다운에서 **검색 가능한 그리드 모달**로 바뀌었다. 검색은 한글 별칭도 받는다('방패' → shield).
- `MenuIconTest`에 계약 테스트 추가 — 기초데이터가 넣는 아이콘 키가 레지스트리에 있는지 검사.
  이 프로젝트는 `tag`(금칙어)를 추가로 쓰는데 그것도 목록에 포함했다.

DB 변경 없음.

## 0.6.1 (CMS 1.2.0) — 공개 식별자 흡수 표기 + 관리자 표시명 관리

CMS 1.2.0(공개 식별자·응답에서 로그인 ID 제거)은 **여기서 먼저 만들어 CMS로 역이식한 것**이다.
`member.handle`·`nickname`, `HandleResolver`, `mineYn`, `PublicIdentityTest`가 이미 있었고
게시글·댓글·모집·후기·쪽지·차단까지 handle 기준으로 동작한다. 그래서 흡수할 코드는 없고,
대조하다 **이 프로젝트에 빠져 있던 구멍 두 개**를 찾아 메웠다.

- **관리자가 표시명을 다룰 수 없었다** — 백엔드 `insert`는 nickname을 받는데 관리자 화면에
  입력칸이 없었고, `updateInfo`에도 nickname이 없어 수정이 불가능했다. 관리자가 만든 계정은
  표시명이 비어 사용자 화면에서 **작성자가 빈칸**으로 보인다(셀프가입은 필수라 드러나지 않았다).
  → 사용자관리에 표시명 컬럼·입력(필수) 추가, `updateInfo`에 nickname 반영.
- `updateInfo`의 nickname은 `COALESCE(NULLIF(#{nickname},''), nickname)` — 값을 안 보내는
  호출이 기존 닉네임을 지우지 않게 한다.

**CMS와의 차이(의도)**
- 표시명이 비었을 때: CMS는 매퍼에서 `COALESCE(nickname,'회원')`로 폴백, 여기서는 **관리자 폼에서
  필수**로 원천 차단한다(커뮤니티라 모든 회원이 글·모집을 쓴다. 닉네임은 유일해야 하고 '회원'이
  여럿 보이면 사칭 구분이 안 된다).
- 메시지 키: CMS는 `error.member.handleRequired`/`error.member.notFound`, 여기는 기존
  `error.member.targetRequired`/`error.auth.memberNotFound`를 그대로 쓴다(이미 여러 도메인이 참조).

DB 변경 없음.

## 0.6.0 (CMS 1.1.9) — 확장설정 흡수 + 사용자 화면 디자인 통일

CMS 1.1.6(확장설정)·1.1.7(사용자 사이트 디자인)·1.1.8(암호화 키 차단)·1.1.9(공통 셸)을 한 번에
흡수했다. 1.1.8의 본체 수정은 이미 충족돼 있었고(`BaseVO`에 `@JsonIgnore`), 회귀 테스트는 0.5.1에서
넣었다.

> **1.1.9는 여기서 먼저 만들어 CMS로 역이식한 것**이다 — 아래 '사용자 화면 전체 디자인 통일'이
> 그 내용이고, CMS는 같은 셸(`PageShell`)을 자기 팔레트(네이비)로 받아갔다. 그래서 이 항목의
> 코드는 CMS 1.1.9와 **구조는 같고 토큰만 다르다**. 다음에 CMS 1.1.9 항목을 볼 땐 이미 반영된
> 것으로 보면 된다.

### 확장설정(CMS 1.1.6) — 그대로 흡수
- `config_item` 테이블 + `ConfigItemService/Controller/PubConfigItemController` + 관리자 화면
  (`/adm/configitem`, 메뉴 51 시스템관리 하위). 정의는 `data.sql`, 화면에서는 값만 바꾼다.
- 이 프로젝트 시드 4건: `site.footer-text` · `site.login-notice` · `main.news-board-id` ·
  `main.hero-badge`(pwsh 추가 — 히어로 배지 문구).

### 사용자 메인 랜딩(CMS 1.1.7 기반, pwsh 사양으로 재작성)
CMS의 `gen.css`/`GenLayout`을 **그대로 가져오지 않았다.** 이 프로젝트는 이미 퍼플 디자인
토큰(`index.css`)과 기능이 많은 헤더(알림·쪽지 SSE 배지·검색·모바일 Drawer)를 갖고 있어,
CMS 파일을 덮으면 `.gen-header`/`.gen-card`/`.gen-h2`/`.gen-eyebrow`/`--gen-line`이 충돌해
게시판·마이페이지까지 함께 깨진다. 그래서 **랜딩에만 필요한 클래스를 새 이름으로** 추가했다.

- `gen/gen.css` 신규 — 히어로·섹션·통계 패널·소식 목록·TOP 버튼. 색·라운드·그림자는
  `index.css`의 `--gen-*`를 그대로 쓴다(토큰 재정의 없음). 제목은 `.gen-h2`와 충돌을 피해
  `.gen-sec-title`.
- 헤더는 **수정자 클래스 하나만 추가**(`.gen-header.is-hero`) — 메인 최상단에서만 투명해지고,
  40px만 내리면 원래 유리면 헤더로 돌아온다. 투명 상태에서는 로고 이미지 대신 사이트명을
  흰 텍스트로 보여준다(업로드 로고는 흰 배경 기준이라 어두운 히어로 위에서 안 보인다).
- 메인 구성: **히어로(풀스크린 슬라이더 3장)** → 소개·통계 → 취미 도감 → 지금 모집 중 →
  이번 주 베스트 → 방금 올라온 글 → 소식(공지).
- **통계 수치는 전부 실제 집계다** — 메인이 이미 조회한 도감·모집·게시글에서 세어 넘긴다.
  별도 통계 API를 만들지 않았다(`/adm/stats`는 관리자 전용). '취미 담기'는 회원 수가 아니라
  담은 횟수 합계라서 라벨을 그렇게 적었다.
- `Reveal`(스크롤 등장) · `useCountUp`(숫자 증가) 이식 — IntersectionObserver·rAF에 기대지 않고
  스크롤 위치 판정 + 타이머 안전망을 쓴다(CMS 1.1.7의 ⚠ 항목: 연출이 실패해도 콘텐츠는 보여야 한다).
  - 이식 중 **CMS에 있던 결함을 실측으로 찾아 양쪽을 고쳤다**: 판정에 `r.bottom > 0`이 있어서
    앵커 이동(#recruit)이나 스크롤 위치 복원처럼 **한 번에 점프**하면 지나친 요소가 영구히
    `opacity: 0`으로 남았다(위로 올라가면 빈 화면). 실제로 페이지 끝까지 내렸을 때 10개 중
    6개가 안 켜졌고, 조건을 뺀 뒤 10/10이 됐다.
  - rAF가 한 번도 안 도는 환경에서 통계 숫자가 타이머 안전망으로 채워지는 것도 실측 확인했다
    (`requestAnimationFrame` 0회, 표시값 15·7·10·25 정상).
- 사이트맵 오버레이는 **가져오지 않았다** — 이 프로젝트는 모바일 메뉴가 이미 Drawer다(중복 UI).
- 갤러리 섹션도 **가져오지 않았다** — 갤러리(board 3)는 GEN 메뉴가 없어 비회원 403이고,
  카드 그리드 역할은 취미 도감 타일이 이미 한다. 그래서 `main.gallery-board-id` 키도 없다.

### 사용자 화면 전체 디자인 통일 (메인만 바뀌면 사이트가 두 개로 보인다)
메인만 랜딩으로 꾸미고 하위 화면은 그대로면 이동할 때마다 다른 사이트처럼 느껴진다.
그래서 **모든 gen 화면을 같은 셸에 얹었다.**

- `common/gen/components/PageShell.tsx` 신규 — `PageHead`(전체폭 헤드 밴드: 분류 eyebrow →
  제목 → 설명 → 우측 액션, 좌측 비주얼 슬롯) + `PageBody`(본문 폭 컨테이너).
- **본문 폭을 두 가지로 고정했다** — 넓게(1080, 목록·카드 그리드)와 `narrow`(900, 읽기 위주).
  기존에는 화면마다 `maxWidth`가 720·860·900·960·1000으로 섞여 있어서 메뉴를 옮길 때마다
  본문 폭이 튀었다. 이제 페이지에서 `maxWidth`를 직접 쓰지 않는다.
- 적용 화면 13곳: 게시판(목록·상세·작성) · FAQ · 모집(목록·상세·등록) · 취미 허브 · 마이페이지 ·
  나의 취미 · 내 피드 · 검색 · 쪽지 · 회원 프로필 · 관심취미 고르기 · 일반페이지 · 없는 주소 안내.
- **헤드는 메인 히어로와 같은 언어다** — 옅은 퍼플 띠로 시작했는데 메인 첫 화면과 톤이 달라
  화면을 옮길 때 여전히 두 사이트처럼 보였다. 그래서 히어로와 같은 어두운 그라디언트 밴드
  (흰 큰 제목·하단 정렬·오버레이)로 바꾸고, 그 아래 얇은 선과 **위치 내비**(홈 › 상위메뉴 › 현재)를
  뒀다. 경로는 `CrumbProvider`가 GEN 메뉴 트리에서 만들어 내리므로 화면마다 적을 필요가 없다.
- **헤더 규칙도 하나로 합쳤다** — 이제 모든 화면에서 최상단 비주얼 위에 투명하게 떠 있고
  40px 내리면 유리면으로 굳는다. 하위 페이지만 유리면 헤더로 두면 톤이 갈린다.
- `Card title`로 화면 제목을 내던 곳은 헤드 밴드로 옮겼다(제목이 두 번 나오지 않게).
  게시판·모집 상세는 **eyebrow에 게시판명/분류, 제목에 글·모임 제목** — 어디의 글인지 항상 보인다.
- `GenLayout`의 하위 페이지 여백을 0으로 바꿨다(헤드 밴드가 화면을 가로지르려면 필요).
  여백은 셸이 책임진다. 푸터도 같은 폭 컨테이너(`.gen-wfix`)로 맞췄다.
- 섹션 제목은 `.gen-h2`, 목록 구분선·배경은 `--gen-line`/`--gen-surface-2` 토큰으로 통일
  (`#f0f0f0`·`#eee`·`#f5f5f5`·`#fafafa` 하드코딩 9곳 제거).
- 히어로 CTA가 `#collection`·`#recruit`로 스크롤하므로 `.gen-section`에 `scroll-margin-top`을 줬다
  (없으면 이동 직후 섹션 제목이 sticky 헤더 뒤에 가린다 — 실측하고 넣었다).

### 함께 고친 것
- **흰 카드 위의 칩이 안 읽히던 문제** — 모집·베스트·최근 글의 분류 칩이 `.gen-tile-chip`
  (컬러 타일용: 흰 반투명 배경 + 흰 글자)을 그대로 써서 **흰 카드 위에서 흰 글자**였다.
  `.gen-tile-chip.is-soft`(퍼플 톤)를 추가해 흰 배경에서는 그걸 쓴다.
- 로그인 화면에 `site.login-notice` 안내문(Alert) 표시, 푸터 저작권 줄을 `site.footer-text`로.
- **헤더에서 내비와 우측 컨트롤이 겹치던 문제** — 로그인 시 항목이 늘면(메뉴 5~6개 + 검색 +
  버튼 4개) 1280px에서도 중앙 내비가 우측 컨트롤과 **159px 겹쳤다**(실측). 그리드 열을
  `1fr auto 1fr` → `auto minmax(0,1fr) auto`로 바꾸고 내비 버튼 고정폭(76px)을 없앴다.
  내비가 뷰포트 정중앙은 아니게 됐지만 겹침보다 낫다.

**DB** — `config_item` 테이블 신규 + 시드 4건 + 메뉴 51(확장설정) + ADMIN 권한.
적용 SQL은 `sql/schema.sql`·`sql/data.sql`의 해당 블록을 그대로 실행하면 된다
(메뉴를 넣은 뒤 `UPDATE config SET menu_version = menu_version + 1`로 메뉴 캐시를 올린다).

⚠ **확인할 것**
1. 메인 소식 게시판(`main.news-board-id`)은 **비로그인이 볼 수 있는 게시판**이어야 한다.
   권한이 없으면 섹션이 조용히 사라진다(의도된 동작 — "글이 없습니다"는 사실과 다르므로).
   기본값 1(공지사항)은 GEN 메뉴 21에 GUEST 권한이 있어 열려 있다. 회귀 방지: `ConfigItemTest`.
2. 히어로 문구는 `gen/main/HeroSection.tsx` 상단 상수다(배지 문구만 확장설정).
3. `gen.css`의 `--gen-header-h`와 `GenLayout`의 `HEADER_H`는 **같은 값**이어야 한다.
   어긋나면 메인 히어로 위에 흰 띠가 남는다.

## 0.5.1 (CMS 1.1.5) — 암호화 키 노출 회귀 테스트

CMS 1.1.8이 고친 취약점(`BaseVO.getCryptoKey()`가 모든 응답에 개인정보 대칭키를 직렬화)은
**여기엔 없었다** — `BaseVO.java:47`에 처음부터 `@JsonIgnore`가 있었다.
하지만 어노테이션이 있다는 사실만으로는 응답이 깨끗하다는 증거가 못 되고,
지워도 컴파일·기존 테스트가 전부 통과하므로 **막을 장치가 없는 상태**였다.

- `CryptoKeyLeakTest` 신규(4건) — 공개 6곳(환경설정·메뉴·팝업·모집·게시글·공개프로필),
  관리자 6곳, 회원 1곳의 응답 본문에 `cryptoKey`도 **키 값 자체**도 없는지 확인한다.
  복호화가 여전히 되는지(`memberName`이 HEX가 아닌 사람이 읽는 값인지)도 함께 본다.
- 실제로 결함을 잡는지 검증했다 — `@JsonIgnore`를 임시로 떼면 4건 중 2건 FAILED.
- `cmsVersion`은 1.1.5 그대로다. CMS 1.1.6(확장설정)·1.1.7(사용자 사이트 디자인)을 아직
  흡수하지 않았고, DDL이 누적이라 1.1.8만 건너뛰어 올릴 수 없다.
  1.1.8의 본체 수정은 이미 충족된 상태이므로, 나중에 1.1.6→1.1.7을 적용하면 그때 1.1.8까지 올린다.

DB 변경 없음.

## 0.5.0 (CMS 1.1.5) — 메시지 소스

CMS 1.1.5를 흡수했다. 상세는 framework의 CHANGELOG 참고. pwsh에 맞춰 바꾼 곳:

- **이관 대상이 102건** — CMS는 17건인데 이 프로젝트는 도메인이 많다
  (모집·후기·쪽지·신고·북마크·좋아요·팔로우·차단·알림·피드·셀프가입·이메일 인증).
  `messages.properties` 키는 **84개**(CMS 26개).
- 문구가 같은데 도메인이 다른 것은 **공용 키로 합쳤다** — `error.common.loginRequired`(9곳),
  `error.common.targetNotFound`, `error.common.invalidTargetType/invalidTarget`,
  `error.common.contentRequired`, `error.member.targetRequired`(회원관리·후기 공용).
  같은 문구에 키를 둘 두면 한쪽만 고쳐 문구가 갈라진다.
- **CMS에 없는 영역 추가**: 모집(21) · 후기(6) · 쪽지(4) · 신고(4) · 이메일 인증(3) ·
  셀프가입/프로필(7) · 차단·팔로우·알림·피드(4).
- `Validate.invalid`도 이관 — CMS의 `Validate`에는 `required`만 있는데 여기엔 형식 검증이 하나 더 있다.
- 알림 제목(`notification.review.arrived`)도 포함 — 에러가 아니지만 사용자에게 보이는 문구다.
- `ProdEnvGuard`의 기동 실패 메시지는 **제외** — 운영자·개발자용이고 응답에 나가지 않는다.

DB 변경 없음.

## 0.4.0 (CMS 1.1.4) — 도메인 이벤트

CMS 1.1.4를 흡수했다. 상세는 framework의 CHANGELOG 참고. pwsh에 맞춰 바꾼 곳:

- **창구를 타야 하는 진입점이 8곳** — CMS는 5곳(로그인·로그아웃·본인 비번변경·관리자 리셋·
  강제로그아웃)이지만 pwsh엔 **비밀번호 재설정·탈퇴·계정정지**가 더 있다.
  전부 `MemberService.invalidateToken`으로 모았다. 이 항목이 이번 흡수의 핵심 이득이다 —
  진입점이 많을수록 세션 닫기를 손으로 짝짓다 빠뜨릴 확률이 높았다.
- **종료 사유 2종을 `SessionEndReason`으로 이전** — `WITHDRAW`·`SUSPEND`가
  `LoginSessionService.END_*`에 있었는데, 이벤트 발행 측(`MemberService`)이 쓰려면
  세션 서비스를 import 해야 해서 결합이 되살아난다. CMS의 4종과 같은 파일로 옮겼다.
- `PasswordEncoder`를 `PasswordEncoderConfig`로 분리 — CMS와 동일하게 순환 의존 회피용.

DB 변경 없음.

## 0.3.0 (CMS 1.1.3) — 접속 세션

CMS 1.1.3을 흡수했다. 상세는 framework의 CHANGELOG 참고. pwsh에 맞춰 바꾼 곳:

- **표시 이름은 닉네임** — 셀프 가입 회원은 실명(`member.name`)이 없을 수 있고 이 서비스의 표기는 닉네임이다.
  목록 매퍼에서 `COALESCE(m.nickname, 복호화한 name)`으로 뽑는다.
- **종료 사유 2종 추가** — CMS의 4종(LOGOUT/FORCE/RELOGIN/PWCHANGE)에 더해
  `WITHDRAW`(셀프 탈퇴) · `SUSPEND`(관리자 정지). 둘 다 pwsh에만 있는 기능이라 CMS에는 없다.
  `AuthService.withdraw`, `MemberService.updateStatus`에서 세션을 닫는다.
- 비밀번호 **재설정**(이메일 인증 경로)도 `PWCHANGE`로 닫는다 — CMS에는 없는 진입점이다.
- 메뉴 id는 pwsh 기준 **50**(회원관리 하위).

## 0.2.0 (CMS 1.1.2) — 접속 IP 제한 · 점검 모드

CMS 1.1.2를 흡수했다. 상세는 framework의 CHANGELOG 참고. pwsh에 맞춰 바꾼 곳:

- **점검 모드 허용 목록** — `/api/auth/**`를 통째로 열지 않는다. pwsh의 auth에는 셀프 회원가입
  (`signup`·`sendSignupCode`)·비밀번호 재설정·탈퇴·프로필 수정이 함께 있어, 프리픽스로 열면
  점검 중에 가입·탈퇴가 진행된다. 세션을 얻고 유지하는 것만 허용:
  `login`, `logout`, `refresh`, `me`, `pwChange`, `pwExtend`.
- **점검 판정은 DAO를 직접 읽는다** — pwsh의 `ConfigService.selectView()`는 비관리자에게
  `title`·`logoFileId` 두 값만 돌려준다. 그걸 쓰면 정작 차단 대상인 비관리자 요청에서 `maintYn`이
  항상 null이 되어 점검 모드가 아무도 못 막는다.
- **중복 IP 등록은 `ErrorCode.DUPLICATE`(409)** — pwsh에는 이 코드가 있다(CMS 쪽은 없어서 400).
- 메뉴 id는 pwsh 기준으로 배정: 접속IP관리 **49**(시스템관리 하위). 금칙어관리 아이콘은 `tag`
  (신고관리가 이미 `flag`를 쓰고 있어 구분).

## 0.1.0 (CMS 1.1.1) — 관리자 편의기능 · 보안 기본기 · 금칙어

CMS 1.1.1을 흡수했다. 커밋 `bf73c52`.

## 0.1.0 이전

위치기반 모집글, 회원가입 약관 동의 등 pwsh 고유 기능은 `git log` 참고.

---

## 아직 흡수하지 않은 CMS 버전

없음 — 현재 CMS 최신(1.1.5)까지 따라와 있다.
