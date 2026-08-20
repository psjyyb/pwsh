package com.pwsh.global.config;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
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
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
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
