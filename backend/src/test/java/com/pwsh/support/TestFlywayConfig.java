package com.pwsh.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 테스트 DB 초기화 전략 — 매 실행 스키마를 비우고 마이그레이션을 처음부터 다시 적용한다.
 *
 * <p>예전에는 {@code test-reset.sql}(DROP SCHEMA) + {@code schema.sql} + {@code data.sql}을
 * {@code spring.sql.init}으로 실행했다. 그러면 스키마 정의가 <b>두 벌</b>(sql/*.sql과 마이그레이션)이 되고,
 * 둘이 어긋나도 테스트는 옛 정의로 통과해 버린다. 그래서 테스트도 같은 마이그레이션을 태운다 —
 * 모든 테스트가 매번 db/migration을 실제로 실행하므로, 마이그레이션이 깨지면 바로 드러난다.
 *
 * <p>운영에는 없는 빈이다(src/test 전용). {@code clean()}은
 * {@code spring.flyway.clean-disabled: false}를 켠 test 프로파일에서만 동작한다.
 */
@Profile("test")
@Configuration
public class TestFlywayConfig {

    @Bean
    FlywayMigrationStrategy cleanBeforeMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
