package com.pwsh.domain.eventlog.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.config.service.ConfigService;
import com.pwsh.domain.loginlog.service.LoginLogService;
import com.pwsh.global.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 이벤트(행위) 로그 기록 서비스. 로그인/등록/수정/삭제 시 event_log에 남긴다.
 * - member_id: 로그인 사용자, device_type/user_agent: 요청 User-Agent 파싱
 * - reg_ip: AuditInterceptor 자동, reg_dt: NOW() (매퍼)
 * - 보관 일수(config.del_log_days)가 지난 활동로그·로그인 이력은 하루 한 번 정리한다(purgeExpiredLogs).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogService {

    private final CommonDAO commonDAO;
    private final ConfigService configService;
    private final LoginLogService loginLogService;

    // ===== 조회(관리자 화면) — append-only라 수정/삭제 없음(보관기간 정리만 배치가 한다) =====
    public List<EventlogVO> selectList(EventlogVO vo) {
        return commonDAO.selectList("eventlogDAO.selectList", vo);
    }

    public int selectListTotalCount(EventlogVO vo) {
        return commonDAO.selectOne("eventlogDAO.selectListTotalCount", vo);
    }

    public EventlogVO selectView(EventlogVO vo) {
        return commonDAO.selectOne("eventlogDAO.selectView", vo);
    }

    // ===== 보관기간 정리 =====

    /**
     * 환경설정 "로그 보관 일수"가 지난 <b>활동로그·로그인 이력</b>을 지운다. 0·빈 값이면 지우지 않는다.
     *
     * <p>★ 대상은 이 둘뿐이다. 개인정보 접근로그(privacy_log)는 법정 최소 보존기간이, 파기 이력(destroy_log)은
     * 증빙 의무가 있어 여기서 지우지 않는다. 메일 이력은 따로 정리한다(MailService, mail.log.retention-days).
     * <p>★ 저장 검증을 거치지 않은 값(직접 SQL·옛 데이터)이 하한보다 짧아도 하한까지만 지운다 —
     * 그대로 쓰면 관리자 접속기록이 보관 의무보다 일찍 사라진다.
     *
     * @return 지운 행 수(활동로그, 로그인 이력)
     */
    public Map<String, Integer> purgeExpiredLogs() {
        // ★ CMS는 selectView()를 쓰지만 이 저장소의 selectView는 비관리자(배치 포함)에게 표시용 값만 준다 —
        //   그대로 쓰면 일수가 늘 비어 정리가 돌지 않는다. 배치 전용 조회를 쓴다.
        String raw = configService.logRetentionDays();
        int days = raw == null || raw.isBlank() ? 0 : Integer.parseInt(raw.trim());
        if (days <= 0) {
            return Map.of("eventLog", 0, "loginLog", 0);
        }
        if (days < ConfigService.MIN_LOG_RETENTION_DAYS) {
            log.warn("로그 보관 일수 {}일이 하한보다 짧아 {}일로 적용합니다", days, ConfigService.MIN_LOG_RETENTION_DAYS);
            days = ConfigService.MIN_LOG_RETENTION_DAYS;
        }
        int events = commonDAO.delete("eventlogDAO.deleteExpired", Map.of("days", String.valueOf(days)));
        int logins = loginLogService.purgeOlderThan(days);
        return Map.of("eventLog", events, "loginLog", logins);
    }

    @Scheduled(cron = "${log.retention.cron:0 30 3 * * *}")
    public void scheduledPurge() {
        Map<String, Integer> deleted = purgeExpiredLogs();
        if (deleted.get("eventLog") + deleted.get("loginLog") > 0) {
            log.info("로그 보관 정리: 활동로그 {}건, 로그인 이력 {}건 삭제", deleted.get("eventLog"), deleted.get("loginLog"));
        }
    }

    /** 행위 기록. targetTable/targetId는 로그인처럼 대상이 없으면 null. */
    public void write(String eventCd, String targetTable, String targetId) {
        EventlogVO vo = new EventlogVO();
        vo.setEventCd(eventCd);
        vo.setMemberId(SecurityUtil.getCurrentMemberId());
        vo.setTargetTable(targetTable);
        vo.setTargetId(targetId);
        String ua = currentUserAgent();
        vo.setUserAgent(ua);
        vo.setDeviceType(parseDevice(ua));
        commonDAO.insert("eventlogDAO.insert", vo);
    }

    private String currentUserAgent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attr) {
            HttpServletRequest req = attr.getRequest();
            return req.getHeader("User-Agent");
        }
        return null;
    }

    /** User-Agent → 기기 유형(간단 판별). 로그인 이력도 같은 기준으로 나눈다(LoginLogService). */
    public static String parseDevice(String ua) {
        if (ua == null) {
            return "unknown";
        }
        String u = ua.toLowerCase();
        if (u.contains("ipad") || u.contains("tablet")) {
            return "tablet";
        }
        if (u.contains("mobi") || u.contains("android")) {
            return "mobile";
        }
        return "desktop";
    }
}
