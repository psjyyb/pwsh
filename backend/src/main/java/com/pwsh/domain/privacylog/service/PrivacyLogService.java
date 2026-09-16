package com.pwsh.domain.privacylog.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.global.log.PrivacyAccessCollector;
import com.pwsh.global.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 개인정보 접근 로그 — 조회(관리자 화면) + 적재. 컨트롤러는 매핑만, 로직은 여기(단일 @Service).
 *
 * <p>적재는 개발자가 부르는 것이 아니라 {@code PrivacyDecryptInterceptor}(탐지) →
 * {@code PrivacyLogFlushInterceptor}(요청 종료 시 1건)로 자동 수행된다.
 * 수정·삭제 API는 두지 않는다 — 접근기록은 고쳐 쓸 수 있으면 기록으로서 의미가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyLogService {

    /** 컬럼 길이 보호(둘 다 VARCHAR(500)) */
    private static final int MAX_TEXT = 500;

    private final CommonDAO commonDAO;

    public List<PrivacylogVO> selectList(PrivacylogVO vo) {
        return commonDAO.selectList("privacylogDAO.selectList", vo);
    }

    public int selectListTotalCount(PrivacylogVO vo) {
        return commonDAO.selectOne("privacylogDAO.selectListTotalCount", vo);
    }

    public PrivacylogVO selectView(PrivacylogVO vo) {
        return commonDAO.selectOne("privacylogDAO.selectView", vo);
    }

    /**
     * 요청 1건분 접근기록 적재. 요청이 끝난 뒤(트랜잭션 밖) 호출되므로 업무 롤백과 무관하다.
     *
     * <p>여기서 예외가 나도 삼킨다 — 응답은 이미 나갔고, 기록 실패로 스택트레이스만 남기는 편이
     * 요청을 깨는 것보다 낫다. 대신 WARN으로 남겨 배선이 끊긴 것을 알 수 있게 한다.
     */
    public void write(String requestUri, PrivacyAccessCollector.Summary summary) {
        try {
            PrivacylogVO vo = new PrivacylogVO();
            vo.setMemberId(SecurityUtil.getCurrentMemberId());
            vo.setRequestUri(cut(requestUri, 255));
            vo.setSqlIds(cut(summary.sqlIds(), MAX_TEXT));
            vo.setTargetIds(cut(summary.targetIds(), MAX_TEXT));
            vo.setAccessCnt(String.valueOf(summary.rowCount()));
            String ua = currentUserAgent();
            vo.setUserAgent(ua);
            vo.setDeviceType(parseDevice(ua));
            commonDAO.insert("privacylogDAO.insert", vo);
        } catch (Exception e) {
            log.warn("개인정보 접근기록 적재 실패 uri={}", requestUri, e);
        }
    }

    private String cut(String s, int max) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String currentUserAgent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attr) {
            HttpServletRequest req = attr.getRequest();
            return req.getHeader("User-Agent");
        }
        return null;
    }

    /** User-Agent → 기기 유형(EventLogService와 같은 기준). */
    private String parseDevice(String ua) {
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
