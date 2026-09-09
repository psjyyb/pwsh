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
  또는 관리자 화면 사이드바 하단(`v0.5.0 · CMS 1.1.5` 형태로 표시).

## CMS를 따라잡는 방법

1. 사이드바 하단에서 현재 `CMS x.y.z`를 확인한다.
2. **framework 저장소의 `CHANGELOG.md`** 에서 그 다음 버전부터 차례로 적용한다(DDL이 누적이라 건너뛰지 않는다).
3. 각 버전의 ⚠ 표시를 반드시 확인한다 — 그대로 옮기면 깨지는 부분이 적혀 있다.
4. 다 옮겼으면 `backend/build.gradle`의 `ext.cmsVersion`을 올리고 이 파일에 기록한다.

---

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
