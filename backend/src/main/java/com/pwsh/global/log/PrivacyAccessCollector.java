package com.pwsh.global.log;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 요청에서 일어난 개인정보 조회를 모아두는 버퍼(요청 스레드 로컬).
 *
 * <p>왜 바로 INSERT하지 않고 모으는가:
 * <ol>
 *   <li><b>업무 트랜잭션과 분리</b> — 조회 시점에 넣으면 그 트랜잭션이 롤백될 때 접근기록도 같이 사라진다.
 *       요청이 끝난 뒤(트랜잭션 밖) 한 번에 남긴다.</li>
 *   <li><b>요청 1건 = 기록 1건</b> — 목록 화면 한 번이 10명을 복호화했다고 10줄이 쌓이면 읽을 수 없다.
 *       "누가 언제 누구를 어느 화면에서" 단위로 합친다.</li>
 *   <li><b>본인 조회를 걷어내려면 합치기 전 단위가 필요</b> — 인증 필터가 매 요청 자기 계정을
 *       복호화해 읽는다. 조회 하나하나를 들고 있어야 그 조회만 골라 뺄 수 있다({@link Access#summarize}).</li>
 * </ol>
 *
 * <p>스레드 로컬이라 요청이 끝나면 반드시 {@link #clear()}해야 한다(스레드 풀 재사용 → 다음 요청에 섞임).
 */
public final class PrivacyAccessCollector {

    /** target_ids 컬럼(500자) 보호 — 대량 조회 시 앞부분만 남기고 "외 n명"으로 줄인다 */
    private static final int MAX_TARGETS = 20;
    /** 한 요청이 남길 조회 항목 상한(비정상 반복 요청이 메모리를 밀어내지 않도록) */
    private static final int MAX_ENTRIES = 50;

    private static final ThreadLocal<Access> HOLDER = new ThreadLocal<>();

    private PrivacyAccessCollector() {
    }

    /** 복호화 조회 1건. targets가 비어 있으면 대상을 특정할 수 없는 조회다(건수 조회 등). */
    public record Entry(String sqlId, Set<String> targets, int rowCount) {
    }

    /** 이 요청에서 남길 최종 값(본인 조회를 걷어낸 뒤). */
    public record Summary(String sqlIds, String targetIds, int rowCount) {
    }

    /** 한 요청분 수집 결과 */
    public static final class Access {

        private final List<Entry> entries = new ArrayList<>();

        void add(Entry e) {
            if (entries.size() < MAX_ENTRIES) {
                entries.add(e);
            }
        }

        /**
         * 기록할 값으로 압축. {@code selfId}(조회자 본인)<b>만</b> 대상인 조회는 통째로 버린다 —
         * 인증 필터가 매 요청 자기 계정을 읽기 때문에, 남겨두면 모든 기록에
         * "본인도 정보주체"로 끼고 조회 건수도 1씩 부풀려진다.
         *
         * @return 남길 것이 없으면 null
         */
        public Summary summarize(String selfId) {
            Set<String> sqlIds = new LinkedHashSet<>();
            Set<String> targets = new LinkedHashSet<>();
            int rowCount = 0;
            int overflow = 0;
            boolean unknown = false;

            for (Entry e : entries) {
                if (isSelfOnly(e, selfId)) {
                    continue;
                }
                sqlIds.add(e.sqlId());
                rowCount += e.rowCount();
                if (e.targets().isEmpty()) {
                    unknown = true;
                    continue;
                }
                for (String t : e.targets()) {
                    if (t.equals(selfId)) {
                        continue; // 남의 정보를 본 김에 딸려 온 본인 행
                    }
                    if (targets.size() >= MAX_TARGETS) {
                        overflow++;
                    } else {
                        targets.add(t);
                    }
                }
            }
            if (sqlIds.isEmpty() || (targets.isEmpty() && !unknown)) {
                return null;
            }
            String targetText = targets.isEmpty()
                    ? "(대상 불명)"
                    : String.join(", ", targets) + (overflow > 0 ? " 외 " + overflow + "명" : "");
            return new Summary(String.join(", ", sqlIds), targetText, rowCount);
        }

        private boolean isSelfOnly(Entry e, String selfId) {
            return selfId != null && e.targets().size() == 1 && e.targets().contains(selfId);
        }
    }

    /** 개인정보 복호화 조회 1건 기록. */
    public static void add(String sqlId, Set<String> targets, int rowCount) {
        Access a = HOLDER.get();
        if (a == null) {
            a = new Access();
            HOLDER.set(a);
        }
        a.add(new Entry(sqlId, targets, rowCount));
    }

    /** 이번 요청에서 수집된 것(없으면 null). */
    public static Access get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
