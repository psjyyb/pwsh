# 파일 참조 모델 · 고아파일 정리(GC) · 복구 (v0.1)

> 모든 파일은 `r_file` 매핑을 통해서만 엔티티에 연결된다(직접 `file_id` 컬럼 금지).
> 고아파일은 스케줄러가 자동 회수(GC)하며, 기본데이터 행은 감사 목적상 보존한다.
> DB 규칙 일반은 [db-conventions.md](db-conventions.md) 참조.

## 1. 파일 참조 모델 (단일 소스 = r_file)

- 파일 실체·메타 = `t_file`, 엔티티와의 연결 = `r_file` (`map_key` + `file_id` + `file_loc` + `ordr`).
- **도메인 테이블에 직접 `file_id` 컬럼을 두지 않는다.** (구 `t_bbs.file_id`/`t_popup.file_id`/`t_config.file_id`는 제거됨)
- 목록 썸네일 등 대표 파일이 필요하면 `r_file`에서 파생 조회한다.
  ```sql
  -- 예: 갤러리 목록 썸네일 = 첫 사진
  (SELECT r.file_id FROM r_file r
    WHERE r.map_key = b.bbs_id AND r.file_loc = 'BBS_IMG'
    ORDER BY r.ordr LIMIT 1) AS file_id
  ```

### file_loc 값 (대문자, `{테이블}` 또는 `{테이블}_{용도}`)
| file_loc | 의미 | 비고 |
|---|---|---|
| `BBS` | 게시글 첨부파일 | |
| `BBS_IMG` | 갤러리 사진 | 캡션=`t_file.file_desc`, 순서=`ordr` |
| `BBS_EDITOR` | 본문 에디터 삽입 이미지 | 추적용(본문 `<img>` 회수 판단) |
| `POPUP` | 팝업 이미지 | 단일 |

## 2. 고아파일이 생기는 3가지 경우와 처리

| 케이스 | 발생 상황 | 처리 방식 | 처리 위치 |
|---|---|---|---|
| **A** | 업로드했지만 저장 안 하고 이탈 (매핑 없는 `t_file`) | GC가 유예시간(기본 24h) 후 회수 | `FileGcService.sweep` |
| **B** | 글 수정 중 첨부/이미지를 뗀 뒤 저장 | 저장 시점에 즉시 hard-delete | `FileController.saveFileMapping` |
| **C** | 글/팝업을 삭제 | 삭제 시 파일 `use_yn='N'` 전파 → GC가 보존기간(기본 180일) 후 회수 | 각 도메인 `delete` + `FileGcService.sweep` |

- **A/C 판정은 도메인 테이블을 보지 않는다.** `t_file`/`r_file`만으로 판정(`fileDAO.selectOrphans`):
  - C: `use_yn='N'` 이고 `upd_dt`(삭제시점)가 보존기간 초과
  - A: `use_yn='Y'` 인데 `r_file` 매핑이 전무하고 `reg_dt`(업로드)가 유예 초과
- GC는 **물리삭제 성공 시에만** `r_file` + `t_file` 행을 제거(반쪽 삭제 방지). 실패 시 행을 남겨 다음 스윕에서 재시도.
- **기본데이터(`t_bbs`/`t_popup` 등) 행은 GC가 절대 건드리지 않는다.** 파일만 회수한다.

## 3. GC 설정 (`application.yml`)

```yaml
file:
  gc:
    retention-days: 180  # C: 삭제된 엔티티 파일 보존기간(일) — 복구 유예
    abandon-hours: 24    # A: 업로드 후 미저장 파일 유예(시간)
    cron: "0 0 4 * * *"  # 매일 새벽 4시 자동 실행
```
- 수동 실행: `POST /api/adm/file/gc.do` (관리자) → 삭제 건수 반환.

## 4. 삭제 전파 규약 ★새 도메인 필수

파일을 `r_file`에 매핑하는 **모든 도메인**은, 엔티티 삭제 코드에서 그 파일들을 `use_yn='N'`으로 전파해야 한다.
누락하면 그 파일은 `use_yn='Y'`+매핑 유지 상태라 A·C 어디에도 안 걸려 **영구 누수**된다. (컴파일러가 못 잡음)

```java
// 엔티티 논리삭제 직후
commonDAO.update("fileDAO.deactivateFilesByOwner",
        Map.of("mapKey", 삭제한엔티티ID, "locs", List.of("해당_file_loc들")));
```
현재 적용 도메인:
- 게시글 `BbsController.delete` → `List.of("BBS","BBS_IMG","BBS_EDITOR")`
- 팝업 `PopupController.delete` → `List.of("POPUP")`

> `deactivateFilesByOwner`는 `r_file`을 **읽어** 대상 파일을 찾아 `t_file.use_yn='N'`만 바꾼다. **`r_file` 매핑은 지우지 않는다**(복구용으로 보존).

## 5. 삭제 데이터 복구 (관리 화면 없음 — 개발자가 직접 SQL)

휴지통 UI는 두지 않는다. 복구가 필요하면 개발자가 아래 SQL을 직접 실행한다.

### 5-1. GC 실행 전(보존기간 이내) — 완전 복구 가능
파일이 아직 살아있으므로 **두 테이블의 `use_yn`만 `Y`로** 되돌리면 된다. `r_file`은 손댈 것 없음.

```sql
-- 게시글 복구 (bbs_id = 복구할 글 ID)
UPDATE t_bbs SET use_yn = 'Y' WHERE bbs_id = :bbsId;

-- 그 글에 묶인 파일 복구 (r_file 매핑으로 범위를 좁혀야 안전)
UPDATE t_file SET use_yn = 'Y'
WHERE file_id IN (
    SELECT file_id FROM r_file
    WHERE map_key = :bbsId AND file_loc IN ('BBS','BBS_IMG','BBS_EDITOR'));
```
```sql
-- 팝업 복구 (pop_id = 복구할 팝업 ID)
UPDATE t_popup SET use_yn = 'Y' WHERE pop_id = :popId;
UPDATE t_file  SET use_yn = 'Y'
WHERE file_id IN (
    SELECT file_id FROM r_file
    WHERE map_key = :popId AND file_loc = 'POPUP');
```

> ⚠️ `t_file`을 조건 없이 전부 `Y`로 돌리지 말 것. 다른 글 삭제로 `N`이 된 파일까지 되살아난다.
> 반드시 위처럼 `r_file`의 `map_key`로 대상을 한정한다.

### 5-2. GC 실행 후(보존기간 경과) — 본문만 복구, 파일은 소실
GC가 물리파일 + `r_file` + `t_file` 행을 이미 삭제했으므로 **파일은 복구 불가**. 기본데이터 행은 남아있어 본문은 살릴 수 있다.

```sql
UPDATE t_bbs SET use_yn = 'Y' WHERE bbs_id = :bbsId;  -- 글만 복구 (이미지·첨부 없음)
```
- 갤러리/팝업처럼 이미지가 핵심인 글은 복구해도 빈 상태가 된다.
- 본문(`context`)에 박힌 에디터 이미지(`<img src="/api/pub/image/{id}">`)는 URL만 남고 파일이 없어 **깨진 이미지(404)** 로 표시된다.

## 6. 요약

| 시점 | t_bbs/t_popup | t_file | r_file | 물리파일 |
|---|---|---|---|---|
| 삭제(soft) 직후 | `use_yn='N'` | `use_yn='N'` | 유지 | 유지 |
| GC 실행 후 | `use_yn='N'` (보존) | 행 삭제 | 삭제 | 삭제 |
| 복구(GC 전) | `use_yn='Y'` | `use_yn='Y'` | 그대로 | 그대로 |
| 복구(GC 후) | `use_yn='Y'` | 없음 | 없음 | 없음 |
