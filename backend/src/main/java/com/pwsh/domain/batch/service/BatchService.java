package com.pwsh.domain.batch.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.eventlog.service.EventLogService;
import com.pwsh.domain.file.service.FileService;
import com.pwsh.domain.mail.service.MailService;
import com.pwsh.domain.member.service.MemberService;
import com.pwsh.domain.recruit.service.RecruitService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

/**
 * 배치 작업 — 주기(DB) 등록 · 예약/즉시 실행 · 실행 이력. 컨트롤러는 매핑만(단일 @Service).
 *
 * <p>v0.6.33(CMS 1.2.33)까지는 작업마다 {@code @Scheduled(cron = "${...}")}였다. 주기를 바꾸려면 yml을 고쳐 재시작해야 했고,
 * 언제 돌았는지·몇 건을 처리했는지는 서버 로그에만 있었다. 이제 주기는 {@code batch_job}이 원본이고,
 * 이 서비스가 기동 때·저장 때 스케줄러에 다시 등록한다.
 *
 * <p>⚠ <b>단일 서버 기준</b>이다. 서버를 여러 대로 늘리면 각자 스케줄러를 갖고 같은 작업을 돌린다 — 겹침은
 * '실행 중' 행 유니크 인덱스가 막지만(먼저 시작한 쪽만 돈다), 주기 변경은 저장한 서버에만 즉시 반영된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService {

    public static final String RUNNING = "BATCH01";
    public static final String SUCCESS = "BATCH02";
    public static final String FAIL = "BATCH03";

    /**
     * 코드에 구현된 작업. 여기 없는 코드의 행은 스케줄러에 올리지 않는다(즉시 실행하면 실패로 남는다).
     * CMS 4종 + 이 저장소 전용 RECRUIT_REMIND(모집 하루 전 알림, 행은 V1001).
     */
    private static final Set<String> JOBS =
            Set.of("MEMBER_LIFECYCLE", "LOG_RETENTION", "FILE_GC", "MAIL_LOG_PURGE", "RECRUIT_REMIND");
    /** 정리 작업을 매초 돌리게 해 두면 DB를 계속 긁는다 — 사람이 정하는 주기는 1분보다 잦을 이유가 없다 */
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(1);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RESULT_MAX = 1000;

    private final CommonDAO commonDAO;
    private final TaskScheduler taskScheduler;
    private final MemberService memberService;
    private final EventLogService eventLogService;
    private final FileService fileService;
    private final MailService mailService;
    private final RecruitService recruitService;

    /**
     * 작업 코드 → 스케줄러에 실제로 걸린 예약과 그 주기. 화면의 scheduledYn·다음 실행 시각은 DB가 아니라 이걸 보고 채운다 —
     * DB만 보면 저장은 됐는데 재등록이 빠진 경우(옛 주기로 계속 도는 상태)가 화면에서 정상으로 보인다.
     */
    private final Map<String, Registered> scheduled = new ConcurrentHashMap<>();

    private record Registered(ScheduledFuture<?> future, String cron) {
    }

    /**
     * 기동 시(그리고 DB를 직접 고친 뒤 다시 맞출 때) — 남아 있는 '실행 중' 이력을 실패로 정리하고 전 작업을 다시 등록한다.
     *
     * <p>★ 정리를 빼면 실행 도중 서버가 내려간 작업은 이력이 영원히 '실행 중'이고, '실행 중' 유니크 인덱스 때문에
     * 그 작업은 다시는 시작되지 않는다(예약 실행도 조용히 건너뛴다).
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void init() {
        int stale = commonDAO.update("batchLogDAO.failStale", Map.of());
        if (stale > 0) {
            log.warn("[Batch] 실행 중으로 남아 있던 이력 {}건을 실패로 정리했다(서버 재시작으로 중단)", stale);
        }
        scheduled.values().forEach(r -> r.future().cancel(false));
        scheduled.clear();
        List<BatchJobVO> jobs = commonDAO.selectList("batchJobDAO.selectList", new BatchJobVO());
        jobs.forEach(this::schedule);
        log.info("[Batch] 작업 {}개 중 {}개를 스케줄러에 등록했다", jobs.size(), scheduled.size());
    }

    public List<BatchJobVO> selectList(BatchJobVO vo) {
        List<BatchJobVO> list = commonDAO.selectList("batchJobDAO.selectList", vo);
        list.forEach(this::fillRuntime);
        return list;
    }

    public int selectListTotalCount(BatchJobVO vo) {
        return commonDAO.selectOne("batchJobDAO.selectListTotalCount", vo);
    }

    public BatchJobVO selectView(BatchJobVO vo) {
        BatchJobVO job = commonDAO.selectOne("batchJobDAO.selectView", vo);
        if (job == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        fillRuntime(job);
        return job;
    }

    public List<BatchLogVO> selectLogList(BatchLogVO vo) {
        return commonDAO.selectList("batchLogDAO.selectList", vo);
    }

    public int selectLogListTotalCount(BatchLogVO vo) {
        return commonDAO.selectOne("batchLogDAO.selectListTotalCount", vo);
    }

    /** 주기·자동 실행 변경 → 저장한 값 그대로 스케줄러에 다시 등록(재시작 불필요). */
    public void update(BatchJobVO vo) {
        selectView(vo); // 없는 작업이면 404
        String cron = vo.getCronExpr() == null ? "" : vo.getCronExpr().trim();
        assertCron(cron);
        vo.setCronExpr(cron);
        vo.setAutoYn("N".equals(vo.getAutoYn()) ? "N" : "Y");
        commonDAO.update("batchJobDAO.update", vo);
        schedule(commonDAO.selectOne("batchJobDAO.selectView", vo));
    }

    /**
     * 즉시 실행. 이력 행은 <b>요청 스레드에서</b> 만든다 — 누른 관리자·IP가 남고, 이미 돌고 있으면 바로 거절할 수 있다.
     * 작업 자체는 스케줄러 스레드에서 돈다(회원 라이프사이클·파일 정리는 데이터가 많으면 오래 걸려 요청을 붙잡으면 안 된다).
     *
     * @return 실행 이력 ID(batch_log_id)
     */
    public String runNow(BatchJobVO vo) {
        String jobCd = selectView(vo).getJobCd();
        BatchLogVO started = start(jobCd, "Y");
        if (started == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.batch.running"));
        }
        String logId = started.getRowId();
        taskScheduler.schedule(() -> execute(jobCd, logId), Instant.now());
        return logId;
    }

    /** 예약 실행(스케줄러가 부른다). 같은 작업이 아직 돌고 있으면 이번 회차는 건너뛴다. */
    public void runScheduled(String jobCd) {
        BatchLogVO started = start(jobCd, "N");
        if (started == null) {
            log.warn("[Batch] {} 이전 실행이 아직 끝나지 않아 이번 예약 실행을 건너뛴다", jobCd);
            return;
        }
        execute(jobCd, started.getRowId());
    }

    /** '실행 중' 행을 만든다. 같은 작업의 '실행 중' 행이 이미 있으면 null(ux_batch_log_running). */
    private BatchLogVO start(String jobCd, String manualYn) {
        BatchLogVO row = new BatchLogVO();
        row.setJobCd(jobCd);
        row.setManualYn(manualYn);
        return commonDAO.insert("batchLogDAO.insertRunning", row) > 0 ? row : null;
    }

    /**
     * 실행 + 결과 기록. ★ 어떤 실패든 이력을 닫는다 — 닫지 않으면 '실행 중'으로 남아 그 작업이 다시는 시작되지 않는다.
     * Error(메모리 부족 등)도 기록한 뒤 다시 던진다(삼키면 JVM 상태가 망가진 걸 아무도 모른다).
     */
    private void execute(String jobCd, String logId) {
        String status = SUCCESS;
        String message;
        Error fatal = null;
        try {
            message = doJob(jobCd);
        } catch (Throwable t) {
            status = FAIL;
            message = t.getClass().getSimpleName() + ": " + t.getMessage();
            log.error("[Batch] {} 실행 실패", jobCd, t);
            if (t instanceof Error err) {
                fatal = err;
            }
        }
        BatchLogVO done = new BatchLogVO();
        done.setRowId(logId);
        done.setStatusCd(status);
        done.setResultMsg(message == null ? null : message.length() <= RESULT_MAX ? message : message.substring(0, RESULT_MAX));
        commonDAO.update("batchLogDAO.finish", done);
        if (fatal != null) {
            throw fatal;
        }
    }

    /** 작업 코드 → 실제로 하는 일. 결과는 화면에 그대로 보이는 처리 건수 요약이다. */
    private String doJob(String jobCd) {
        return switch (jobCd) {
            case "MEMBER_LIFECYCLE" -> memberService.runLifecycle();
            case "LOG_RETENTION" -> {
                Map<String, Integer> d = eventLogService.purgeExpiredLogs();
                yield String.format("활동로그 %d건, 로그인 이력 %d건, 배치 이력 %d건 삭제",
                        d.get("eventLog"), d.get("loginLog"), d.get("batchLog"));
            }
            case "FILE_GC" -> String.format("고아 파일 %d건 삭제", fileService.sweep());
            case "MAIL_LOG_PURGE" -> String.format("메일 이력 %d건 삭제", mailService.purgeExpiredLogs());
            // 같은 날 다시 돌려도 이미 보낸 수신자는 매퍼가 거른다 — 즉시 실행해도 중복 알림이 나가지 않는다
            case "RECRUIT_REMIND" -> String.format("내일 모임 알림 %d건 발송",
                    recruitService.sendMeetReminders(LocalDate.now().plusDays(1).toString()));
            default -> throw new IllegalStateException("코드에 구현되지 않은 작업입니다: " + jobCd);
        };
    }

    /** 작업 하나를 (다시) 등록한다. 자동 실행이 꺼졌거나 구현 없는 코드·잘못된 주기면 등록하지 않는다. */
    private synchronized void schedule(BatchJobVO job) {
        Registered old = scheduled.remove(job.getJobCd());
        if (old != null) {
            old.future().cancel(false); // 이미 돌고 있는 회차는 끝까지 돈다 — 다음 회차부터 새 주기
        }
        if (!"Y".equals(job.getAutoYn()) || !JOBS.contains(job.getJobCd())
                || !CronExpression.isValidExpression(job.getCronExpr())) {
            return;
        }
        String jobCd = job.getJobCd();
        String cron = job.getCronExpr();
        scheduled.put(jobCd, new Registered(taskScheduler.schedule(() -> runScheduled(jobCd), new CronTrigger(cron)), cron));
    }

    private void fillRuntime(BatchJobVO job) {
        Registered r = scheduled.get(job.getJobCd());
        boolean on = r != null && !r.future().isCancelled();
        job.setScheduledYn(on ? "Y" : "N");
        LocalDateTime next = on ? CronExpression.parse(r.cron()).next(LocalDateTime.now()) : null;
        job.setNextRunDt(next == null ? null : next.format(DT));
    }

    /** cron 검증 — 형식(6자리), 다시 오지 않는 주기, 1분보다 잦은 주기를 거절한다. */
    private static void assertCron(String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.batch.invalidCron"));
        }
        CronExpression expr = CronExpression.parse(cron);
        LocalDateTime t = expr.next(LocalDateTime.now());
        if (t == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.batch.neverRuns"));
        }
        // 앞으로 몇 회차의 간격을 본다 — "*/30 * * * * *"처럼 일정하게 잦은 주기와 "0,10 * * * * *"처럼 몰린 주기 모두 걸린다
        for (int i = 0; i < 10; i++) {
            LocalDateTime n = expr.next(t);
            if (n == null) {
                break;
            }
            if (Duration.between(t, n).compareTo(MIN_INTERVAL) < 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.batch.tooFrequent"));
            }
            t = n;
        }
    }
}
