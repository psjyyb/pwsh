package com.pwsh.global.security.jwt;

import com.pwsh.global.security.CustomUserDetails;
import com.pwsh.global.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 헤더의 JWT를 검증하고 SecurityContext에 인증정보를 설정한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        // access 토큰만 인증에 사용(refresh 토큰을 API 호출에 오용하는 것 차단)
        if (token != null && jwtTokenProvider.validate(token)
                && "access".equals(jwtTokenProvider.getType(token))
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String userId = jwtTokenProvider.getUserId(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(userId);
            // 토큰 버전 대조: 불일치(다른 기기 새 로그인·로그아웃으로 token_ver 증가)면 인증 거부(→ 401).
            // 구버전 토큰(ver 없음)도 거부 → 재로그인 유도. 단일세션(last-wins)·로그아웃 즉시 무효화의 핵심.
            String tokenVer = jwtTokenProvider.getVer(token);
            String currentVer = ((CustomUserDetails) userDetails).getTokenVer();
            if (tokenVer != null && tokenVer.equals(currentVer)) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
