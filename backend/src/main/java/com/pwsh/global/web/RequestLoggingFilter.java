package com.pwsh.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 요청 접근 로그 — 메서드·경로·상태·소요시간. 운영 모니터링용.
 * 소음 조절: 정상(2xx/3xx)=DEBUG, 클라이언트오류(4xx)=INFO, 서버오류(5xx)=WARN.
 *   (운영 기본 로그레벨 INFO에서는 문제(4xx/5xx)만 남고, dev(DEBUG)에서는 전부 남는다.)
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
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
        }
    }
}
