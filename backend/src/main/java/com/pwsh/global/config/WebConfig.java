package com.pwsh.global.config;

import com.pwsh.global.security.AccessIpInterceptor;
import com.pwsh.global.security.PermissionInterceptor;
import com.pwsh.global.web.ClientIpInterceptor;
import com.pwsh.global.web.MaintenanceInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ClientIpInterceptor clientIpInterceptor;
    private final AccessIpInterceptor accessIpInterceptor;
    private final MaintenanceInterceptor maintenanceInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ClientIpInterceptor가 먼저 IP를 담아야 AccessIpInterceptor가 읽을 수 있다(등록 순서 = 실행 순서)
        registry.addInterceptor(clientIpInterceptor);
        // 점검 모드 — 관리자 외 전 API 차단 (/api/** 전체가 대상이라 경로를 좁히지 않는다)
        registry.addInterceptor(maintenanceInterceptor).addPathPatterns("/api/**");
        // 관리자 계정의 접속 IP 제한 (/api/adm/**)
        registry.addInterceptor(accessIpInterceptor).addPathPatterns("/api/adm/**");
        // 관리자 API 메뉴 권한 강제 (/api/adm/**)
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/adm/**");
    }
}
