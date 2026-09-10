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
  또는 관리자 화면 사이드바 하단(`v0.6.0 · CMS 1.1.8` 형태로 표시).

## CMS를 따라잡는 방법

1. 사이드바 하단에서 현재 `CMS x.y.z`를 확인한다.
2. **framework 저장소의 `CHANGELOG.md`** 에서 그 다음 버전부터 차례로 적용한다(DDL이 누적이라 건너뛰지 않는다).
3. 각 버전의 ⚠ 표시를 반드시 확인한다 — 그대로 옮기면 깨지는 부분이 적혀 있다.
4. 다 옮겼으면 `backend/build.gradle`의 `ext.cmsVersion`을 올리고 이 파일에 기록한다.

---

## 0.6.0 (CMS 1.1.8) — 확장설정 흡수 + 사용자 메인 랜딩 개편

CMS 1.1.6(확장설정)·1.1.7(사용자 사이트 디자인)·1.1.8(암호화 키 차단)을 한 번에 흡수했다.
1.1.8의 본체 수정은 이미 충족돼 있었고(`BaseVO`에 `@JsonIgnore`), 회귀 테스트는 0.5.1에서 넣었다.

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
