package com.pwsh.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더. {@code SecurityConfig}에서 떼어낸 이유가 있다.
 *
 * <p>SecurityConfig는 도메인 서비스를 주입받는다(필터 구성에 필요). 그런데 비밀번호를 다루는
 * 도메인 서비스는 거꾸로 PasswordEncoder가 필요하다 → 같은 클래스에 두면
 * {@code securityConfig → ...Service → passwordEncoder(securityConfig)} 순환이 되어 기동이 실패한다.
 * 아무것도 주입받지 않는 이 설정으로 옮겨 두면 어느 방향에서 참조해도 순환이 생기지 않는다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
