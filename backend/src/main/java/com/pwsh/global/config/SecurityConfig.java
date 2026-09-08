package com.pwsh.global.config;

import com.pwsh.domain.loginsession.service.LoginSessionService;
import com.pwsh.global.security.CustomUserDetailsService;
import com.pwsh.global.security.RestAccessDeniedHandler;
import com.pwsh.global.security.RestAuthenticationEntryPoint;
import com.pwsh.global.security.jwt.JwtAuthenticationFilter;
import com.pwsh.global.security.jwt.JwtTokenProvider;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 설정. (standard-template-spec.md 5)
 * - 세션 미사용(STATELESS), JWT 기반
 * - /api/auth/** 는 permitAll, 나머지 인증 필요
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final LoginSessionService loginSessionService;

    /** CSP 정책 문자열. 빈 값이면 헤더 미전송(에디터·외부 리소스 때문에 정책이 필요한 프로젝트만 켠다). */
    @Value("${security.headers.csp:}")
    private String cspPolicy;

    /** HSTS max-age(초). 0이면 미전송 — HTTP 개발 환경에서 https 강제 전환을 막기 위한 기본값. */
    @Value("${security.headers.hsts-max-age:0}")
    private long hstsMaxAge;

    /** Referrer-Policy 값. 기본은 외부로 경로를 흘리지 않는 strict-origin-when-cross-origin. */
    @Value("${security.headers.referrer-policy:strict-origin-when-cross-origin}")
    private String referrerPolicy;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // 보안 응답 헤더. 값은 security.headers.* 로 조정하고, 빈 값이면 그 헤더를 보내지 않는다.
                //  - CSP: XSS·데이터 유출 완화. 에디터/외부 이미지가 있으면 정책을 넓혀야 한다.
                //  - HSTS: HTTPS 환경에서만 의미. HTTP 개발 환경에서 켜면 브라우저가 https로 강제 전환해 접속이 막힌다
                //    → 기본 0(미전송)으로 두고 운영에서만 값을 준다.
                .headers(h -> {
                    h.frameOptions(f -> f.sameOrigin());                       // 클릭재킹: 외부 iframe 삽입 차단
                    h.contentTypeOptions(Customizer.withDefaults());           // MIME 스니핑 차단(nosniff)
                    h.referrerPolicy(r -> r.policy(referrerPolicyOf(referrerPolicy)));
                    if (!cspPolicy.isBlank()) {
                        h.contentSecurityPolicy(c -> c.policyDirectives(cspPolicy));
                    }
                    if (hstsMaxAge > 0) {
                        h.httpStrictTransportSecurity(s -> s.maxAgeInSeconds(hstsMaxAge).includeSubDomains(true));
                    } else {
                        h.httpStrictTransportSecurity(s -> s.disable());
                    }
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/pwExtend", "/api/auth/pwChange", "/api/auth/logout", "/api/auth/nickname", "/api/auth/me", "/api/auth/updateProfileImage", "/api/auth/withdraw").authenticated() // 본인 인증 필요(순서상 permitAll보다 먼저)
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/pub/**").permitAll() // 공개 읽기 전용(에디터 이미지 서빙 등)
                        // 사용자(GEN) 사이트의 조회 엔드포인트는 비로그인 허용(공개 범위는 GUEST 권한그룹이 메뉴로 결정).
                        // 쓰기(insert/update/delete)·관리 목록은 아래 anyRequest().authenticated()로 보호.
                        .requestMatchers(
                                "/api/adm/menu/selectMenuListTree.do",
                                "/api/adm/config/selectConfigView.do",
                                // 공통코드 콤보(지역·취미레벨 등) — 공개 화면의 필터가 쓴다.
                                // 막아두면 게스트가 모집 목록에 들어서는 순간 401 → 로그인 화면으로 튕긴다.
                                "/api/adm/code/selectCodeListCombo.do",
                                // 게시글 첨부 목록 — 글 열람 권한으로 FileController가 다시 인가한다.
                                "/api/adm/file/selectFileMapList.do",
                                "/api/adm/board/selectBoardView.do",
                                "/api/adm/board/selectBoardListCombo.do",
                                "/api/adm/post/selectPostList.do",
                                "/api/adm/post/selectPostListWeeklyBest.do",
                                "/api/adm/post/selectPostView.do",
                                "/api/adm/comment/selectCommentList.do",
                                "/api/adm/page/selectPageView.do",
                                // 약관: 가입 화면의 동의 항목·푸터 링크가 비로그인 상태에서 읽는다.
                                "/api/adm/policy/selectPolicyListPublic.do",
                                "/api/adm/policy/selectPolicyView.do",
                                "/api/adm/popup/selectPopupListMain.do",
                                "/api/adm/recruit/selectRecruitList.do",
                                "/api/adm/recruit/selectRecruitView.do",
                                "/api/adm/hobby/selectHobbyList.do",
                                "/api/adm/hobby/selectHobbyView.do",
                                "/api/adm/search/selectSearchAll.do",
                                "/api/adm/review/selectReviewList.do",
                                "/api/adm/review/selectReviewListStats.do").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)   // 미인증 → 401
                        .accessDeniedHandler(accessDeniedHandler))            // 권한부족 → 403
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService, loginSessionService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 설정 문자열 → Spring Security의 Referrer-Policy enum. 오타·미지원 값은 기본값으로 떨어뜨린다. */
    private static ReferrerPolicy referrerPolicyOf(String value) {
        for (ReferrerPolicy p : ReferrerPolicy.values()) {
            if (p.getPolicy().equalsIgnoreCase(value)) {
                return p;
            }
        }
        return ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN;
    }

    /** CORS. 허용 오리진은 cors.allowed-origins(콤마구분, 기본 localhost:3000)로 설정. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
