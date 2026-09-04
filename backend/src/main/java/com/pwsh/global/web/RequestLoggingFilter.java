package com.pwsh.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 요청 접근 로그 — 메서드·경로·상태·소요시간. 운영 모니터링용.
 * 소음 조절: 정상(2xx/3xx)=DEBUG, 클라이언트오류(4xx)=INFO, 서버오류(5xx)=WARN.
 *   (운영 기본 로그레벨 INFO에서는 문제(4xx/5xx)만 남고, dev(DEBUG)에서는 전부 남는다.)
 *
 * <p><b>요청 추적 ID</b>: 요청마다 8자리 ID를 MDC({@value #MDC_KEY})에 넣는다. 로그 패턴에 %X{reqId}가
 * 있으면 한 요청에서 나온 로그가 같은 ID로 묶여, 동시 요청이 섞인 로그에서도 흐름을 따라갈 수 있다.
 * 응답 헤더 {@value #HEADER}로도 내보내 화면에서 본 오류를 로그에서 바로 찾을 수 있다.
 * 클라이언트가 헤더로 보낸 ID가 있으면 이어받는다(프록시·프론트에서 시작한 추적 유지).
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "reqId";
    public static final String HEADER = "X-Request-Id";

    /** 로그 한 줄이 길어지지 않게 8자로 자른다(동시 요청 구분에는 충분). */
    private static final int ID_LEN = 8;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String reqId = req.getHeader(HEADER);
        if (reqId == null || reqId.isBlank() || reqId.length() > 64) {
            reqId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, ID_LEN);
        }
        MDC.put(MDC_KEY, reqId);
        res.setHeader(HEADER, reqId);
        try {
            chain.doFilter(req, res);
        } finally {
            String uri = req.getRequestURI();
            if (uri.startsWith("/api/") && !"OPTIONS".equals(req.getMethod())) {
                long ms = System.currentTimeMillis() - start;
                int status = res.getStatus();
                if (status >= 500) {
                    log.warn("{} {} -> {} ({}ms)", req.getMethod(), uri, status, ms);
                } else if (status >= 400) {
                    log.info("{} {} -> {} ({}ms)", req.getMethod(), uri, status, ms);
                } else {
                    log.debug("{} {} -> {} ({}ms)", req.getMethod(), uri, status, ms);
                }
            }
            MDC.remove(MDC_KEY); // 스레드 재사용(풀) 시 이전 요청 ID가 남지 않도록 반드시 제거
        }
    }
}
