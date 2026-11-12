package com.pwsh.domain.config.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.accessip.service.AccessIpService;
import com.pwsh.domain.mail.service.MailService;
import com.pwsh.domain.member.service.MemberVO;
import com.pwsh.global.security.SecurityUtil;
import java.util.ArrayList;
import java.util.List;
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
    private final MailService mailService;

    private final AtomicReference<MaintSnapshot> maintCache = new AtomicReference<>();

    /** 점검 모드 상태 — on이면 관리자 외 요청을 503으로 막는다. */
    public record MaintStatus(boolean on, String message) {
    }

    private record MaintSnapshot(MaintStatus status, long loadedAt) {
    }

    /**
     * 설정 조회. 사이트명·로고는 로그인 화면과 사용자 화면도 필요해서 이 API는 권한 예외로 열려 있다
     * (PermissionInterceptor의 EXEMPT_SUFFIX, SecurityConfig permitAll). 그래서 관리자가 아니면 표시용 두 값만
     * 돌려준다 — 잠금 임계값·비번 만료일 같은 보안 정책값을 아무 방문자에게 알려줄 이유가 없다(1.2.25).
     *
     * <p>★ 서버 안에서 설정값이 필요하면 이 메서드를 쓰지 않는다 — 배치·필터처럼 관리자 로그인이 없는 문맥에서는
     * 표시용 두 값만 돌아와 나머지가 늘 비어 보인다({@link #maintStatus()}·{@link #logRetentionDays()} 참고).
     */
    public ConfigVO selectView() {
        ConfigVO vo = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        if (vo == null || SecurityUtil.isAdmin()) {
            return vo;
        }
        ConfigVO display = new ConfigVO();
        display.setTitle(vo.getTitle());
        display.setLogoFileId(vo.getLogoFileId());
        display.setSignupYn(vo.getSignupYn()); // 로그인 화면의 "회원가입" 링크 표시용(보안 정책값이 아니다)
        return display;
    }

    /**
     * 로그 보관 일수(정리 배치용).
     *
     * <p>★ {@link #selectView()}가 아니라 DAO를 직접 읽는다 — 배치는 로그인한 사람이 없는 문맥이라
     * selectView가 표시용 두 값만 돌려줘 일수가 늘 비고, 그러면 정리가 영원히 돌지 않는다(회귀 방지: LogRetentionTest).
     */
    public String logRetentionDays() {
        ConfigVO vo = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        return vo == null ? null : vo.getDelLogDays();
    }

    /**
     * 관리자 2차 인증 사용 여부(로그인용).
     *
     * <p>★ {@link #selectView()}가 아니라 DAO를 직접 읽는다 — 로그인 요청은 관리자 로그인 전이라 selectView가
     * 표시용 두 값만 돌려줘, 그걸 쓰면 2차 인증이 영원히 꺼진 것처럼 동작한다.
     */
    public boolean adminOtpEnabled() {
        ConfigVO vo = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        return vo != null && "Y".equals(vo.getAdminOtpYn());
    }

    /** 셀프 회원가입 허용 여부 — 가입 API가 매 요청 본다(DAO 직접, 비로그인 요청이라 selectView의 표시 필터와 무관하게) */
    public boolean signupEnabled() {
        ConfigVO vo = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        return vo != null && "Y".equals(vo.getSignupYn());
    }

    public void update(ConfigVO vo) {
        assertLogRetention(vo.getDelLogDays());
        if (vo.getSignupYn() != null && !vo.getSignupYn().isBlank()) {
            vo.setSignupYn("Y".equals(vo.getSignupYn()) ? "Y" : "N");
            // 켜는 순간에만 본다 — 인증 코드를 못 보내면 아무도 가입할 수 없다
            if ("Y".equals(vo.getSignupYn()) && !signupEnabled() && !mailService.isEnabled()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.config.signupMailDisabled"));
            }
        }
        if ("Y".equals(vo.getAccIpYn())) {
            // 자기 IP를 등록하지 않고 제한을 켜면 즉시 스스로 잠긴다(복구는 DB SQL뿐) → 사전 차단
            accessIpService.assertNotSelfLockout();
        }
        if (vo.getAdminOtpYn() != null && !vo.getAdminOtpYn().isBlank()) {
            vo.setAdminOtpYn("Y".equals(vo.getAdminOtpYn()) ? "Y" : "N");
            // 켜는 순간에만 본다 — 이미 켜진 상태에서 사이트명 하나 바꾸는 저장까지 막지 않는다
            if ("Y".equals(vo.getAdminOtpYn()) && !adminOtpEnabled()) {
                assertAdminOtpReady();
            }
        }
        commonDAO.update("configDAO.update", vo);
        accessIpService.evict(); // 접속 IP 제한 사용여부가 바뀌었을 수 있으므로 캐시를 즉시 버린다
        maintCache.set(null);    // 점검 모드도 마찬가지 — 껐는데 30초간 막혀 있으면 안 된다
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

    /**
     * 관리자 2차 인증을 켜도 되는지 — 메일을 못 보내거나 이메일이 없는 관리자가 있으면 켜는 순간 그 관리자가(메일이 꺼져
     * 있으면 전원이) 로그인할 수 없게 된다. 복구는 DB SQL뿐이라 저장 전에 막고, 이유를 모두 한 번에 알려 준다.
     */
    private void assertAdminOtpReady() {
        List<String> reasons = new ArrayList<>();
        if (!mailService.isEnabled()) {
            reasons.add(Messages.get("error.config.otpMailDisabled"));
        }
        List<String> noEmail = commonDAO.selectList("memberDAO.selectAdminIdsWithoutEmail", new MemberVO());
        if (!noEmail.isEmpty()) {
            reasons.add(Messages.get("error.config.otpNoEmail", String.join(", ", noEmail)));
        }
        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    Messages.get("error.config.otpNotReady", String.join(" ", reasons)));
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
