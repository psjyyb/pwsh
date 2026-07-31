package com.pwsh.global.config;

import com.pwsh.global.security.PermissionInterceptor;
import com.pwsh.global.web.ClientIpInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ClientIpInterceptor clientIpInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(clientIpInterceptor);
        // 관리자 API 메뉴 권한 강제 (/api/adm/**)
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/adm/**");
    }
}
