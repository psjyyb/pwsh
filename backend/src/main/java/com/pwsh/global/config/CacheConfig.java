package com.pwsh.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 설정(Caffeine, 단일 JVM).
 *
 * <p>캐시에 올리는 기준은 <b>"자주 읽고, 거의 안 바뀌고, 누가 읽든 결과가 같은 것"</b>이다.
 * 세 번째 조건이 특히 중요하다 — 사람마다 결과가 다른 조회를 캐시에 올리면
 * <b>먼저 조회한 사람의 결과가 다음 사람에게 그대로 나간다.</b>
 *
 * <p>★ 그래서 <b>메뉴 트리는 캐시하지 않는다.</b> 메뉴는 로그인 여부·권한그룹에 따라 결과가
 * 달라지므로(비로그인=GUEST) 키를 잘못 잡는 순간 권한 없는 메뉴가 노출된다.
 * 굳이 캐시하려면 실효 권한그룹 집합을 키에 넣어야 하는데, 그 키를 만드는 비용이
 * 조회 비용과 비슷해서 이득이 없다.
 *
 * <p>★ 캐시를 새로 추가하면 <b>무효화 지점을 반드시 같이 넣는다.</b> 등록·수정·삭제에
 * {@code @CacheEvict}가 빠지면 화면에서 값을 바꿔도 반영되지 않고, 원인을 찾기 매우 어렵다
 * (서버를 재시작하면 고쳐지는 것처럼 보인다). 새 캐시 이름은 {@link #CACHE_NAMES}에 등록해야
 * 시스템 상태 화면의 현황·비우기에 잡힌다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 공통코드 콤보(pCodeId별 하위 코드 목록). 메뉴·화면 대부분이 매 요청 조회한다 */
    public static final String CODE_COMBO = "codeCombo";

    /** 이 애플리케이션이 쓰는 캐시 이름 전부 — 시스템 상태 화면이 이 목록으로 현황을 만든다 */
    public static final List<String> CACHE_NAMES = List.of(CODE_COMBO);

    /** 보관 시간. 짧게 두면 무효화를 빠뜨려도 곧 복구된다(안전망) */
    @Value("${cache.ttl-minutes:30}")
    private long ttlMinutes;

    /** 캐시별 최대 항목 수. 넘으면 오래 안 쓴 것부터 버린다 */
    @Value("${cache.max-size:500}")
    private long maxSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CACHE_NAMES.toArray(new String[0]));
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(maxSize)
                .recordStats()); // 시스템 상태 화면의 적중률 표시용
        // 선언하지 않은 이름으로 @Cacheable을 쓰면 조용히 새 캐시가 생겨 현황에서 빠진다 → 막는다
        manager.setAllowNullValues(false);
        return manager;
    }
}
