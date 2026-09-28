package com.pwsh.domain.system.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.config.service.ConfigVO;
import com.pwsh.global.config.CacheConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 시스템 상태 · 캐시 · 설정 백업(단일 @Service).
 *
 * <p>운영 중에 "지금 어느 버전이 떠 있나, 마이그레이션은 어디까지 적용됐나, 디스크는 남았나,
 * 배치는 언제 도나"를 물을 곳이 없었다. 서버에 들어가 로그를 뒤져야 알 수 있었다.
 *
 * <p>⚠ <b>읽기 전용이다.</b> 앱이 {@code pg_dump}를 돌리거나 파일시스템을 건드리게 만들지 않는다 —
 * 실행 권한·경로·용량이 배포 환경마다 다르고, 실패해도 조용히 반쪽 백업이 남는다.
 * 실제 DB 백업은 운영 절차(cron + pg_dump)로 하고, 여기서는 그 절차에 필요한 정보
 * (DB 크기·스키마 버전·업로드 용량)와 <b>설정 스냅샷</b>만 제공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final CommonDAO commonDAO;
    private final com.pwsh.global.realtime.RealtimeService realtimeService;
    private final CacheManager cacheManager;
    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildProperties;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.gc.cron:0 0 4 * * *}")
    private String fileGcCron;

    @Value("${mail.log.cron:0 30 4 * * *}")
    private String mailLogCron;

    @Value("${member.lifecycle.cron:0 0 3 * * *}")
    private String lifecycleCron;

    @Value("${recruit.remind.cron:0 0 9 * * *}")
    private String recruitRemindCron;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    /** 화면 한 장에 들어가는 상태 전부. 실패한 항목은 통째로 죽지 않고 그 칸만 비운다. */
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", app());
        result.put("db", db());
        result.put("disk", disk());
        result.put("schedules", schedules());
        result.put("caches", caches());
        result.put("realtime", realtime());
        return result;
    }

    private Map<String, Object> app() {
        BuildProperties build = buildProperties.getIfAvailable();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", build == null ? "dev" : build.getVersion());
        m.put("cmsVersion", build == null ? "dev" : build.get("cmsVersion"));
        m.put("buildTime", build == null || build.getTime() == null ? null : build.getTime().toString());
        m.put("profiles", String.join(", ", environment.getActiveProfiles()));
        m.put("javaVersion", System.getProperty("java.version"));
        // 가동 시간은 JVM 기준이다 — 배포 시각이 아니라 마지막 재시작 시각을 뜻한다
        Duration up = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        m.put("uptime", String.format("%d일 %d시간 %d분", up.toDays(), up.toHoursPart(), up.toMinutesPart()));
        Runtime rt = Runtime.getRuntime();
        m.put("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        m.put("heapMaxMb", rt.maxMemory() / 1024 / 1024);
        m.put("mailEnabled", mailEnabled ? "Y" : "N");
        return m;
    }

    private Map<String, Object> db() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            m.put("size", commonDAO.selectOne("systemDAO.selectDbSize", Map.of()));
            m.put("version", commonDAO.selectOne("systemDAO.selectDbVersion", Map.of()));
            // ★ 스키마가 어느 마이그레이션까지 적용됐는지 — 배포 사고에서 가장 먼저 봐야 하는 값이다
            m.put("migrations", commonDAO.selectList("systemDAO.selectMigrations", Map.of()));
        } catch (RuntimeException e) {
            // flyway_schema_history가 없는 환경(수동 구축 DB 등)에서도 화면은 떠야 한다
            log.warn("[System] DB 상태 조회 실패: {}", e.getMessage());
            m.put("migrations", List.of());
        }
        return m;
    }

    private Map<String, Object> disk() {
        Map<String, Object> m = new LinkedHashMap<>();
        File root = new File(uploadDir).getAbsoluteFile();
        m.put("path", root.getPath());
        // 업로드 경로가 아직 없으면 그 위 존재하는 디렉터리를 기준으로 남은 용량을 잰다
        File probe = root;
        while (probe != null && !probe.exists()) {
            probe = probe.getParentFile();
        }
        if (probe != null) {
            m.put("totalMb", probe.getTotalSpace() / 1024 / 1024);
            m.put("freeMb", probe.getUsableSpace() / 1024 / 1024);
        }
        long[] used = uploadUsage(root.toPath());
        m.put("uploadMb", used[0] / 1024 / 1024);
        m.put("uploadFiles", used[1]);
        return m;
    }

    /** 업로드 디렉터리 사용량 [바이트, 파일 수]. 읽기 실패한 항목은 건너뛴다(권한·경합). */
    private long[] uploadUsage(Path dir) {
        if (!Files.isDirectory(dir)) {
            return new long[] {0, 0};
        }
        long bytes = 0;
        long count = 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                try {
                    bytes += Files.size(p);
                    count++;
                } catch (Exception ignore) {
                    // 세는 도중 지워진 파일 — 총량이 조금 어긋나도 화면을 못 띄우는 것보다 낫다
                }
            }
        } catch (Exception e) {
            log.warn("[System] 업로드 용량 집계 실패: {}", e.getMessage());
        }
        return new long[] {bytes, count};
    }

    /**
     * 배치 cron. 다음 실행 시각까지 계산하지 않는다 — 스케줄러 내부 상태에 기대면 깨지기 쉽다.
     * SSE 연결 유지 핑(25초 fixedDelay)은 운영 배치가 아니라 빼고, 연결 수는 realtime 항목으로 보여준다.
     */
    private List<Map<String, Object>> schedules() {
        return List.of(
                Map.of("name", "고아 파일 정리", "cron", fileGcCron),
                Map.of("name", "메일 이력 정리", "cron", mailLogCron),
                Map.of("name", "회원 라이프사이클", "cron", lifecycleCron),
                Map.of("name", "모집 하루 전 알림", "cron", recruitRemindCron));
    }

    /**
     * 실시간(SSE) 연결 현황. 이 JVM에 붙어 있는 것만 센다 —
     * 인스턴스를 늘리면 각자 자기 연결만 보므로 합계가 아니다.
     */
    private Map<String, Object> realtime() {
        int[] stats = realtimeService.connectionStats();
        return Map.of("connections", stats[0], "members", stats[1]);
    }

    /** 캐시 현황(이름·항목 수·적중률). Caffeine이 아니면 통계 없이 이름만 보여준다. */
    public List<Map<String, Object>> caches() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String name : CacheConfig.CACHE_NAMES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            org.springframework.cache.Cache c = cacheManager.getCache(name);
            if (c instanceof CaffeineCache caffeine) {
                Cache<Object, Object> native0 = caffeine.getNativeCache();
                m.put("size", native0.estimatedSize());
                CacheStats stats = native0.stats();
                m.put("hit", stats.hitCount());
                m.put("miss", stats.missCount());
                m.put("hitRate", Math.round(stats.hitRate() * 1000) / 10.0); // 소수 1자리 %
            }
            list.add(m);
        }
        return list;
    }

    /**
     * 캐시 전부 비우기. 설정을 DB에서 직접 바꿨을 때처럼 <b>앱이 모르는 변경</b>을 반영시키는 수단이다
     * (정상 경로는 각 서비스의 {@code @CacheEvict}가 처리한다).
     *
     * @return 비운 캐시 수
     */
    public int evictAll() {
        int n = 0;
        for (String name : CacheConfig.CACHE_NAMES) {
            org.springframework.cache.Cache c = cacheManager.getCache(name);
            if (c != null) {
                c.clear();
                n++;
            }
        }
        log.info("[System] 캐시 {}개를 비웠다", n);
        return n;
    }

    /**
     * 설정 스냅샷 — 운영자가 화면에서 채운 값(환경설정·공통코드·메뉴·확장설정)을 그대로 내려준다.
     *
     * <p>⚠ <b>DB 백업이 아니다.</b> 게시글·회원 같은 운영 데이터는 담지 않는다(개인정보가 섞이고
     * 용량이 커진다). 용도는 "설정을 다른 환경으로 옮기거나, 바꾸기 전 상태를 남겨두는 것"이다.
     * 되돌리는 기능(import)은 제공하지 않는다 — 부분 복원은 PK 충돌·참조 깨짐을 만들기 쉬워
     * 개발자가 내용을 보고 판단해야 한다.
     */
    public Map<String, Object> configBackup() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exportedAt", java.time.LocalDateTime.now().toString());
        BuildProperties build = buildProperties.getIfAvailable();
        m.put("version", build == null ? "dev" : build.getVersion());
        m.put("config", commonDAO.selectOne("configDAO.selectView", new ConfigVO()));
        m.put("codes", commonDAO.selectList("systemDAO.selectBackupCodes", Map.of()));
        m.put("menus", commonDAO.selectList("systemDAO.selectBackupMenus", Map.of()));
        m.put("configItems", commonDAO.selectList("systemDAO.selectBackupConfigItems", Map.of()));
        return m;
    }
}
