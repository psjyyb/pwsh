package com.pwsh.domain.config.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.accessip.service.AccessIpService;
import com.pwsh.global.security.SecurityUtil;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 환경설정(단일 행) 업무 로직. 컨트롤러는 매핑만(단일 @Service). */
@Service
@RequiredArgsConstructor
public class ConfigService {

    /** 점검 모드 판정은 모든 요청마다 필요하므로 짧게 캐시한다(설정 저장 시 즉시 무효화). */
    private static final long MAINT_CACHE_TTL_MS = 30_000L;

    /**
     * 로그 보관 일수 하한(0=정리 안 함은 예외). 활동로그·로그인 이력에는 관리자의 접속기록이 섞여 있어
     * 너무 짧게 잡으면 접속기록 보관 의무를 어길 수 있다 — 1년 아래로는 지우지 않는다.
     * 저장 검증과 정리 배치(EventLogService.purgeExpiredLogs)가 같은 값을 쓴다.
     */
    public static final int MIN_LOG_RETENTION_DAYS = 365;

    private final CommonDAO commonDAO;
    private final AccessIpService accessIpService;

    private final AtomicReference<MaintSnapshot> maintCache = new AtomicReference<>();

    /** 점검 모드 상태 — on이면 관리자 외 요청을 503으로 막는다. */
    public record MaintStatus(boolean on, String message) {
    }

    private record MaintSnapshot(MaintStatus status, long loadedAt) {
    }

    /**
     * 설정 조회. 사이트명·로고는 로그인 화면과 사용자 화면도 필요해서 이 API는 권한 예외로 열려 있다
     * (PermissionInterceptor의 EXEMPT_SUFFIX). 그래서 관리자가 아니면 표시용 두 값만 돌려준다 —
     * 잠금 임계값·비번 만료일 같은 보안 정책값을 아무 방문자에게 알려줄 이유가 없다.
     */
    public ConfigVO selectView() {
        ConfigVO vo = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        if (vo == null || SecurityUtil.isAdmin()) {
            return vo;
        }
        ConfigVO display = new ConfigVO();
        display.setTitle(vo.getTitle());
        display.setLogoFileId(vo.getLogoFileId());
        return display;
    }

    public void update(ConfigVO vo) {
        assertLogRetention(vo.getDelLogDays());
        if ("Y".equals(vo.getAccIpYn())) {
            // 자기 IP를 등록하지 않고 제한을 켜면 즉시 스스로 잠긴다(복구는 DB SQL뿐) → 사전 차단
            accessIpService.assertNotSelfLockout();
        }
        commonDAO.update("configDAO.update", vo);
        accessIpService.evict(); // 접속 IP 제한 사용여부가 바뀌었을 수 있으므로 캐시를 즉시 버린다
        maintCache.set(null);    // 점검 모드도 마찬가지 — 껐는데 30초간 막혀 있으면 안 된다
    }

    /**
     * 로그 보관 일수(정리 배치용).
     *
     * <p>★ {@link #selectView()}가 아니라 DAO를 직접 읽는다 — 배치는 로그인한 사람이 없는 문맥이라
     * selectView가 표시용 두 값(사이트명·로고)만 돌려줘 일수가 늘 비고, 그러면 정리가 영원히 돌지 않는다
     * ({@link #maintStatus()}와 같은 이유. 회귀 방지: LogRetentionTest).
     */
    public String logRetentionDays() {
        ConfigVO vo = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        return vo == null ? null : vo.getDelLogDays();
    }

    /**
     * 로그 보관 일수: 빈 값·0(정리 안 함)·하한 이상만 허용. 빈 값은 매퍼가 NULL로 저장하고
     * 정리 배치는 NULL을 0과 같이 "정리 안 함"으로 본다.
     */
    private void assertLogRetention(String days) {
        if (days == null || days.isBlank()) {
            return;
        }
        int d;
        try {
            d = Integer.parseInt(days.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    Messages.get("error.config.logRetentionTooShort", MIN_LOG_RETENTION_DAYS));
        }
        if (d < 0 || (d > 0 && d < MIN_LOG_RETENTION_DAYS)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    Messages.get("error.config.logRetentionTooShort", MIN_LOG_RETENTION_DAYS));
        }
    }

    /** 점검 모드 캐시 무효화 — 설정을 DB에서 직접 바꿨을 때 즉시 반영시키는 용도. */
    public void evictMaint() {
        maintCache.set(null);
    }

    /**
     * 점검 모드 상태(캐시).
     *
     * <p>★ {@link #selectView()}가 아니라 DAO를 직접 읽는다 — selectView는 비관리자에게
     * 표시용 두 값만 돌려주므로, 그걸 쓰면 정작 차단해야 할 비관리자 요청에서 maintYn이
     * 항상 null이 되어 점검 모드가 아무도 못 막는다.
     */
    public MaintStatus maintStatus() {
        MaintSnapshot cur = maintCache.get();
        if (cur != null && System.currentTimeMillis() - cur.loadedAt() < MAINT_CACHE_TTL_MS) {
            return cur.status();
        }
        ConfigVO config = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        MaintStatus fresh = new MaintStatus(
                config != null && "Y".equals(config.getMaintYn()),
                config == null ? null : config.getMaintMessage());
        maintCache.set(new MaintSnapshot(fresh, System.currentTimeMillis()));
        return fresh;
    }
}
