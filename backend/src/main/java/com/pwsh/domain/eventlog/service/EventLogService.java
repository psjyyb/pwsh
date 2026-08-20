package com.pwsh.domain.eventlog.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.global.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 이벤트(행위) 로그 기록 서비스. 로그인/등록/수정/삭제 시 event_log에 남긴다.
 * - member_id: 로그인 사용자, device_type/user_agent: 요청 User-Agent 파싱
 * - reg_ip: AuditInterceptor 자동, reg_dt: NOW() (매퍼)
 */
@Service
@RequiredArgsConstructor
public class EventLogService {

    private final CommonDAO commonDAO;

    // ===== 조회(관리자 화면) — append-only라 수정/삭제 없음 =====
    public List<EventlogVO> selectList(EventlogVO vo) {
        return commonDAO.selectList("eventlogDAO.selectList", vo);
    }

    public int selectListTotalCount(EventlogVO vo) {
        return commonDAO.selectOne("eventlogDAO.selectListTotalCount", vo);
    }

    public EventlogVO selectView(EventlogVO vo) {
        return commonDAO.selectOne("eventlogDAO.selectView", vo);
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

    /** User-Agent → 기기 유형(간단 판별). */
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
