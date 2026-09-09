package com.pwsh.domain.loginsession.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.common.event.SessionEndReason;
import com.pwsh.domain.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 접속 세션 기록·조회. 컨트롤러는 매핑만, 로직은 여기(단일 @Service).
 *
 * <p>JWT가 무상태라 서버는 "지금 누가 접속 중인지"를 스스로 모른다. 그래서 로그인 때 행을 하나 열고,
 * 인증된 요청이 올 때마다 {@link #touch}로 마지막 활동 시각을 갱신해 접속 현황을 만든다.
 * 세션 종료(로그아웃·강제종료·다른 기기 로그인)는 행을 지우지 않고 end_dt/end_reason으로 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSessionService {

    /**
     * 마지막 활동 시각을 DB에 반영하는 최소 간격(ms).
     * 요청마다 UPDATE를 날리면 조회 API 한 번에 쓰기 한 번이 붙는다 —
     * 세션 만료가 분 단위라 1분 해상도면 충분하다.
     */
    private static final long TOUCH_INTERVAL_MS = 60_000L;

    private final CommonDAO commonDAO;
    private final MemberService memberService;

    /** memberId → 마지막으로 DB에 반영한 시각. 단일 JVM 기준(스케일아웃하면 각 인스턴스가 각자 갱신). */
    private final Map<String, Long> lastTouched = new ConcurrentHashMap<>();

    // ===== 조회(관리자 화면) =====
    public List<LoginSessionVO> selectList(LoginSessionVO vo) {
        return commonDAO.selectList("loginSessionDAO.selectList", vo);
    }

    public int selectListTotalCount(LoginSessionVO vo) {
        return commonDAO.selectOne("loginSessionDAO.selectListTotalCount", vo);
    }

    public LoginSessionVO selectView(LoginSessionVO vo) {
        return commonDAO.selectOne("loginSessionDAO.selectView", vo);
    }

    // ===== 기록 =====

    /**
     * 로그인 — 이전 세션을 RELOGIN으로 닫고 새 세션을 연다.
     * 단일세션(last-wins)이라 앞선 세션은 어차피 token_ver 증가로 무효화되므로 여기서도 닫아준다.
     */
    public void open(String memberId) {
        close(memberId, SessionEndReason.RELOGIN);
        LoginSessionVO vo = new LoginSessionVO();
        vo.setMemberId(memberId);
        String ua = currentUserAgent();
        vo.setUserAgent(ua);
        vo.setDeviceType(parseDevice(ua));
        commonDAO.insert("loginSessionDAO.insert", vo);
        lastTouched.put(memberId, System.currentTimeMillis());
    }

    /** 세션 종료 기록. 열린 세션이 없으면 아무 일도 하지 않는다. */
    public void close(String memberId, String endReason) {
        if (memberId == null || memberId.isBlank() || "system".equals(memberId)) {
            return;
        }
        LoginSessionVO vo = new LoginSessionVO();
        vo.setMemberId(memberId);
        vo.setEndReason(endReason);
        commonDAO.update("loginSessionDAO.closeByMember", vo);
        lastTouched.remove(memberId);
    }

    /**
     * 마지막 활동 시각 갱신(요청마다 호출). {@link #TOUCH_INTERVAL_MS} 간격으로 묶어서 DB에 반영한다.
     *
     * <p>세션 기록 실패가 요청 자체를 막으면 안 되므로 예외는 삼키고 로그만 남긴다 —
     * 이 값은 "언제까지 활동했나"를 보여주는 부가 정보이지 인증 판단 근거가 아니다.
     */
    public void touch(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastTouched.get(memberId);
        if (prev != null && now - prev < TOUCH_INTERVAL_MS) {
            return;
        }
        lastTouched.put(memberId, now);
        try {
            LoginSessionVO vo = new LoginSessionVO();
            vo.setMemberId(memberId);
            commonDAO.update("loginSessionDAO.touch", vo);
        } catch (RuntimeException e) {
            log.warn("[LoginSession] 활동시각 갱신 실패 — memberId={}", memberId, e);
        }
    }

    // ===== 관리자 강제종료 =====

    /**
     * 세션 1건 강제종료. 세션 행만 닫으면 상대는 토큰으로 계속 쓸 수 있으므로
     * token_ver를 올려 발급된 토큰(access·refresh)을 함께 무효화한다.
     */
    public void forceEnd(LoginSessionVO vo) {
        LoginSessionVO target = selectView(vo);
        if (target == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, Messages.get("error.loginsession.notFound"));
        }
        // 세션 닫기는 직접 하지 않는다 — 토큰 무효화 창구가 이벤트를 발행하고,
        // 그 이벤트를 받는 리스너가 close()를 호출한다(경로를 하나로 유지).
        memberService.invalidateToken(target.getMemberId(), SessionEndReason.FORCE);
    }

    private String currentUserAgent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attr) {
            HttpServletRequest req = attr.getRequest();
            return req.getHeader("User-Agent");
        }
        return null;
    }

    /** User-Agent → 기기 유형(간단 판별). event_log와 같은 기준. */
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
