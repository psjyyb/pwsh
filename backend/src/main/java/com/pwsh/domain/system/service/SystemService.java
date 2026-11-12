package com.pwsh.domain.system.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.accessip.service.AccessIpService;
import com.pwsh.domain.batch.service.BatchJobVO;
import com.pwsh.domain.config.service.ConfigService;
import com.pwsh.domain.config.service.ConfigVO;
import com.pwsh.domain.mail.service.MailService;
import com.pwsh.domain.policy.service.PolicyService;
import com.pwsh.global.config.CacheConfig;
import com.pwsh.global.config.DataInitializer;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final MailService mailService;
    private final com.pwsh.global.realtime.RealtimeService realtimeService;
    private final ConfigService configService;
    private final AccessIpService accessIpService;
    private final PolicyService policyService;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;
    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildProperties;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    // 설치 점검용 — 값은 판정에만 쓰고 결과에 싣지 않는다
    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${security.headers.hsts-max-age:0}")
    private long hstsMaxAge;

    @Value("${security.headers.csp:}")
    private String cspPolicy;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String corsOrigins;

    @Value("${site.login-url:}")
    private String siteLoginUrl;

    /** 화면 한 장에 들어가는 상태 전부. 실패한 항목은 통째로 죽지 않고 그 칸만 비운다. */
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", app());
        result.put("db", db());
        result.put("disk", disk());
        result.put("schedules", schedules());
        result.put("caches", caches());
        result.put("realtime", realtime());
        result.put("mail", mailService.settings());
        return result;
    }

    /** 메일 설정 점검 발송(템플릿 없이 고정 문구) → 결과·사유. 이력에도 남는다. */
    public MailService.SendResult mailCheck(String toEmail) {
        return mailService.sendCheck(toEmail.trim());
    }

    // ===== 설치 점검 =====

    private static final String OK = "OK";
    private static final String WARN = "WARN";
    private static final String FAIL = "FAIL";
    /** 저장소에 적힌 개발 기본 비밀값(application-dev.yml의 JWT_SECRET 기본값·BaseVO의 APP_CRYPTO_KEY 기본값) */
    private static final String KNOWN_DEV_SECRET = "psjyyb";
    /** HS256 키로 쓰기에 짧은 길이 — JwtTokenProvider가 SHA-256으로 늘리긴 하지만 추측 가능한 짧은 값은 그대로 약하다 */
    private static final int MIN_SECRET_LENGTH = 32;
    private static final long MIN_FREE_MB = 1024;

    /**
     * 설치 점검 — 운영에 올리기 전에 빠뜨리기 쉬운 설정을 한 장으로 본다(CMS 1.2.35).
     *
     * <p>★ <b>비밀값 자체는 어떤 형태로도 싣지 않는다</b>(암호화 키·JWT 비밀키·비밀번호). "확인용"으로 보여 주면 이 화면이 곧 유출
     * 통로다 — 기본값과 같은지·길이가 충분한지만 판정해 결과로 낸다.
     * <p>★ 이 목록은 "어떤 방어가 꺼져 있는지"의 지도라 관리자만 본다(컨트롤러 requireAdmin).
     * <p>상태 화면과 달리 업로드 폴더에 임시 파일을 하나 만들었다 지운다(쓰기 권한은 써 봐야 안다).
     *
     * @return {summary: {OK, WARN, FAIL}, list: [{key, group, item, level, message, fix}]}
     */
    public Map<String, Object> checks() {
        List<Map<String, Object>> list = new ArrayList<>();
        checkSecurity(list);
        checkStorage(list);
        checkOperation(list);
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String lv : List.of(FAIL, WARN, OK)) {
            summary.put(lv, (int) list.stream().filter(m -> lv.equals(m.get("level"))).count());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("list", list);
        return result;
    }

    private void checkSecurity(List<Map<String, Object>> list) {
        String crypto = System.getenv("APP_CRYPTO_KEY");
        if (crypto == null || crypto.isBlank()) {
            add(list, "CRYPTO_KEY", "보안", "개인정보 암호화 키", FAIL,
                    "환경변수 APP_CRYPTO_KEY가 없어 저장소 코드에 적힌 기본 키로 개인정보(이름·연락처·이메일)를 암호화하고 있다",
                    "운영 서버에 APP_CRYPTO_KEY를 넣는다. ⚠ 이미 저장된 개인정보는 지금 키로 암호화돼 있어 키를 바꾸면 복호화되지 않는다 — 첫 운영 데이터가 쌓이기 전에 정한다");
        } else {
            add(list, "CRYPTO_KEY", "보안", "개인정보 암호화 키", OK, "환경변수로 주입됨", null);
        }

        if (jwtSecret == null || jwtSecret.isBlank() || KNOWN_DEV_SECRET.equals(jwtSecret)) {
            add(list, "JWT_SECRET", "보안", "로그인 토큰 서명 키", FAIL,
                    "로그인 토큰 서명 키가 비었거나 개발 기본값이다 — 이 값을 아는 사람은 관리자 토큰을 직접 만들 수 있다",
                    "환경변수 JWT_SECRET에 32자 이상의 임의 문자열을 넣는다(바꾸면 기존 로그인이 모두 풀린다)");
        } else if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            add(list, "JWT_SECRET", "보안", "로그인 토큰 서명 키", WARN,
                    "로그인 토큰 서명 키가 " + MIN_SECRET_LENGTH + "자보다 짧다", "JWT_SECRET을 " + MIN_SECRET_LENGTH + "자 이상의 임의 문자열로");
        } else {
            add(list, "JWT_SECRET", "보안", "로그인 토큰 서명 키", OK, "기본값이 아니고 길이도 충분하다", null);
        }

        String adminPw = commonDAO.selectOne("systemDAO.selectMemberPassword", Map.of("memberId", "admin"));
        if (adminPw != null && passwordEncoder.matches(DataInitializer.DEFAULT_ADMIN_PW, adminPw)) {
            add(list, "ADMIN_DEFAULT_PW", "보안", "기본 관리자 비밀번호", FAIL,
                    "기본 관리자 계정(admin)이 처음 만들어진 비밀번호 그대로다 — 이 틀의 문서에 적혀 있는 값이다",
                    "admin으로 로그인해 비밀번호를 바꾼다(관리자 상단 > 비밀번호 변경)");
        } else {
            add(list, "ADMIN_DEFAULT_PW", "보안", "기본 관리자 비밀번호", OK,
                    adminPw == null ? "기본 관리자 계정(admin)이 없다(또는 사용 안 함)" : "바뀌었다", null);
        }

        String userPw = commonDAO.selectOne("systemDAO.selectMemberPassword", Map.of("memberId", "user"));
        if (userPw != null) {
            boolean defaultPw = passwordEncoder.matches(DataInitializer.DEFAULT_USER_PW, userPw);
            add(list, "SAMPLE_ACCOUNT", "보안", "샘플 계정", WARN,
                    "샘플 일반 계정(user)이 남아 있다" + (defaultPw ? " — 비밀번호도 처음 값 그대로다" : ""),
                    "운영에서 쓰지 않으면 회원관리에서 user 계정을 삭제한다");
        } else {
            add(list, "SAMPLE_ACCOUNT", "보안", "샘플 계정", OK, "샘플 일반 계정(user)이 없다", null);
        }

        if (configService.adminOtpEnabled()) {
            add(list, "ADMIN_OTP", "보안", "관리자 2차 인증", OK, "켜져 있다", null);
        } else {
            add(list, "ADMIN_OTP", "보안", "관리자 2차 인증", WARN,
                    "꺼져 있다 — 관리자 비밀번호만 새면 관리자 화면이 열린다",
                    "메일 발송을 갖춘 뒤 환경설정 > 관리자 2차 인증을 켠다");
        }

        ConfigVO cfg = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        if (accessIpService.isEnforced()) {
            add(list, "ACCESS_IP", "보안", "관리자 접속 IP 제한", OK, "허용 IP에서만 관리자 로그인이 된다", null);
        } else if (cfg != null && "Y".equals(cfg.getAccIpYn())) {
            add(list, "ACCESS_IP", "보안", "관리자 접속 IP 제한", WARN,
                    "켜져 있지만 허용 IP가 하나도 없어 실제로는 막지 않는다(잠금 방지 규칙)",
                    "접속IP관리에 사무실 IP를 등록한다");
        } else {
            add(list, "ACCESS_IP", "보안", "관리자 접속 IP 제한", WARN,
                    "꺼져 있다 — 관리자 화면이 어디서나 열린다", "접속IP관리에 허용 IP를 등록한 뒤 환경설정에서 켠다");
        }

        List<String> profiles = List.of(environment.getActiveProfiles());
        if (profiles.contains("prod")) {
            add(list, "PROFILE", "보안", "실행 프로파일", OK, "prod", null);
        } else {
            add(list, "PROFILE", "보안", "실행 프로파일", WARN,
                    "운영(prod)이 아닌 프로파일로 실행 중이다(" + (profiles.isEmpty() ? "기본" : String.join(", ", profiles)) + ")",
                    "운영은 SPRING_PROFILES_ACTIVE=prod — DB·JWT 비밀키를 환경변수로 강제하고 암호화 키가 없으면 기동하지 않는다");
        }

        if (hstsMaxAge > 0) {
            add(list, "HSTS", "보안", "HTTPS 강제(HSTS)", OK, "보낸다", null);
        } else {
            add(list, "HSTS", "보안", "HTTPS 강제(HSTS)", WARN, "보내지 않는다",
                    "HTTPS로 운영하면 security.headers.hsts-max-age=31536000 (HTTP 개발 환경에서는 켜지 않는다)");
        }
        if (cspPolicy != null && !cspPolicy.isBlank()) {
            add(list, "CSP", "보안", "콘텐츠 보안 정책(CSP)", OK, "보낸다", null);
        } else {
            add(list, "CSP", "보안", "콘텐츠 보안 정책(CSP)", WARN, "보내지 않는다 — 스크립트 주입이 생겼을 때 막아 줄 마지막 장치가 없다",
                    "security.headers.csp에 정책을 넣는다(에디터·외부 이미지 출처를 포함해 화면을 확인하며)");
        }
        if (corsOrigins.contains("localhost") || corsOrigins.contains("127.0.0.1")) {
            add(list, "CORS", "보안", "허용 오리진(CORS)", WARN, "개발 주소(localhost)가 허용돼 있다",
                    "cors.allowed-origins를 운영 도메인으로 바꾼다");
        } else {
            add(list, "CORS", "보안", "허용 오리진(CORS)", OK, "개발 주소가 없다", null);
        }
    }

    private void checkStorage(List<Map<String, Object>> list) {
        try {
            List<String> failed = commonDAO.selectList("systemDAO.selectFailedMigrations", Map.of());
            if (!failed.isEmpty()) {
                add(list, "MIGRATION", "저장소", "DB 마이그레이션", FAIL, "실패한 마이그레이션이 있다: " + String.join(", ", failed),
                        "원인을 고친 뒤 실패 이력을 정리한다(flyway repair) — 실패 이력이 남아 있으면 다음 기동이 막힌다");
            } else {
                String latest = commonDAO.selectOne("systemDAO.selectLatestMigration", Map.of());
                add(list, "MIGRATION", "저장소", "DB 마이그레이션", OK, "실패 없음(최신 V" + latest + ")", null);
            }
        } catch (RuntimeException e) {
            add(list, "MIGRATION", "저장소", "DB 마이그레이션", WARN, "Flyway 이력을 읽을 수 없다(수동 구축 DB)",
                    "스키마는 마이그레이션으로만 관리한다 — 수동 구축이면 baseline 후 이력을 맞춘다");
        }

        File root = new File(uploadDir).getAbsoluteFile();
        if (!root.isDirectory()) {
            add(list, "UPLOAD_DIR", "저장소", "업로드 폴더", WARN, "아직 없다(첫 업로드 때 만든다): " + root.getPath(),
                    "상위 폴더에 쓰기 권한이 있는지 확인하거나 미리 만들어 둔다");
        } else if (!writable(root.toPath())) {
            add(list, "UPLOAD_DIR", "저장소", "업로드 폴더", FAIL, "쓸 수 없다: " + root.getPath(),
                    "실행 계정에 쓰기 권한을 준다 — 이대로면 모든 파일 업로드가 실패한다");
        } else if (!new File(uploadDir).isAbsolute()) {
            add(list, "UPLOAD_DIR", "저장소", "업로드 폴더", WARN, "실행 위치 기준 상대경로다(" + uploadDir + ")",
                    "FILE_UPLOAD_DIR에 절대경로를 준다 — 실행 위치가 바뀌면 다른 폴더를 보게 되고 재배포 때 유실될 수 있다");
        } else if (root.getUsableSpace() / 1024 / 1024 < MIN_FREE_MB) {
            add(list, "UPLOAD_DIR", "저장소", "업로드 폴더", WARN, "디스크 여유가 1GB 미만이다", "디스크를 늘리거나 미디어 라이브러리에서 정리한다");
        } else {
            add(list, "UPLOAD_DIR", "저장소", "업로드 폴더", OK, "쓸 수 있다", null);
        }
    }

    private void checkOperation(List<Map<String, Object>> list) {
        Map<String, Object> mail = mailService.settings();
        if (!"Y".equals(mail.get("enabled"))) {
            add(list, "MAIL", "운영", "메일 발송", WARN,
                    "꺼져 있다 — 가입 인증코드·비밀번호 찾기·휴면 안내·폼 결과·관리자 2차 인증 코드 메일이 나가지 않고 이력만 남는다",
                    "MAIL_ENABLED=true와 MAIL_USERNAME·MAIL_PASSWORD를 넣고 위 '메일 설정 점검'으로 실제 발송을 확인한다");
        } else if (!"Y".equals(mail.get("senderSet"))) {
            add(list, "MAIL", "운영", "메일 발송", FAIL, "켜져 있지만 발신 계정이 없어 모든 발송이 실패한다",
                    "MAIL_USERNAME·MAIL_PASSWORD를 넣는다");
        } else {
            add(list, "MAIL", "운영", "메일 발송", OK, "켜져 있다(실제 수신은 '메일 설정 점검'으로 확인)", null);
        }

        if (siteLoginUrl.contains("localhost") || siteLoginUrl.contains("127.0.0.1")) {
            add(list, "LOGIN_URL", "운영", "안내메일 로그인 링크", WARN, "개발 주소다(" + siteLoginUrl + ")",
                    "SITE_LOGIN_URL을 운영 주소로 — 휴면 안내메일의 로그인 버튼이 이 주소로 간다");
        } else {
            add(list, "LOGIN_URL", "운영", "안내메일 로그인 링크", OK, siteLoginUrl, null);
        }

        List<String> off = new ArrayList<>();
        List<String> failedJobs = new ArrayList<>();
        for (BatchJobVO job : commonDAO.<BatchJobVO>selectList("batchJobDAO.selectList", new BatchJobVO())) {
            if (!"Y".equals(job.getAutoYn())) {
                off.add(job.getName());
            }
            if ("BATCH03".equals(job.getLastStatusCd())) {
                failedJobs.add(job.getName());
            }
        }
        if (off.isEmpty() && failedJobs.isEmpty()) {
            add(list, "BATCH", "운영", "배치 작업", OK, "모두 자동 실행 중이고 마지막 실행에 실패가 없다", null);
        } else {
            List<String> parts = new ArrayList<>();
            if (!off.isEmpty()) {
                parts.add("자동 실행 꺼짐: " + String.join(", ", off));
            }
            if (!failedJobs.isEmpty()) {
                parts.add("마지막 실행 실패: " + String.join(", ", failedJobs));
            }
            add(list, "BATCH", "운영", "배치 작업", WARN, String.join(" / ", parts),
                    "배치 작업 관리에서 실행 이력의 사유를 확인하고 즉시 실행으로 다시 돌려 본다");
        }

        ConfigVO cfg = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        if (cfg == null || !"Y".equals(cfg.getSignupYn())) {
            add(list, "SIGNUP", "운영", "셀프 회원가입", OK, "꺼져 있다(회원은 관리자가 만든다)", null);
        } else if (!"Y".equals(mail.get("enabled"))) {
            add(list, "SIGNUP", "운영", "셀프 회원가입", FAIL, "켜져 있지만 메일 발송이 꺼져 있어 인증코드를 보낼 수 없다 — 아무도 가입할 수 없다",
                    "메일 발송을 켜거나 회원가입을 끈다");
        } else if (policyService.selectListRequiredIds().isEmpty()) {
            add(list, "SIGNUP", "운영", "셀프 회원가입", WARN, "켜져 있지만 필수 약관이 없어 약관 동의 없이 가입된다",
                    "약관관리에서 이용약관·개인정보처리방침을 필수(req_yn)로 등록한다");
        } else {
            add(list, "SIGNUP", "운영", "셀프 회원가입", OK, "켜져 있고 필수 약관이 있다", null);
        }

        if (cfg != null && "Y".equals(cfg.getMaintYn())) {
            add(list, "MAINT", "운영", "점검 모드", WARN, "켜져 있다 — 관리자 외 모든 요청이 막혀 있다", "점검이 끝났으면 환경설정에서 끈다");
        } else {
            add(list, "MAINT", "운영", "점검 모드", OK, "꺼져 있다", null);
        }
    }

    /** 쓰기 권한은 실제로 써 봐야 안다 — 임시 파일을 만들었다 바로 지운다. */
    private static boolean writable(Path dir) {
        try {
            Path probe = Files.createTempFile(dir, ".install-check", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void add(List<Map<String, Object>> list, String key, String group, String item, String level,
                            String message, String fix) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("group", group);
        m.put("item", item);
        m.put("level", level);
        m.put("message", message);
        if (fix != null) {
            m.put("fix", fix);
        }
        list.add(m);
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
        m.put("mailEnabled", mailService.isEnabled() ? "Y" : "N");
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
     * 배치 주기 요약 — 원본은 batch_job(배치 작업 관리 화면)이다. 실행·변경·이력은 그 화면에서 한다.
     * v0.6.33까지는 yml의 cron을 읽었는데, 주기를 DB로 옮긴 뒤 여기만 yml을 보면 화면과 실제가 어긋난다.
     * SSE 연결 유지 핑(25초 fixedDelay)은 운영 배치가 아니라 빼고(@Scheduled 그대로), 연결 수는 realtime 항목으로 보여준다.
     */
    private List<Map<String, Object>> schedules() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (BatchJobVO job : commonDAO.<BatchJobVO>selectList("batchJobDAO.selectList", new BatchJobVO())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", job.getName());
                m.put("cron", job.getCronExpr());
                m.put("autoYn", job.getAutoYn());
                if (job.getLastStatusName() != null) {
                    m.put("lastStatusName", job.getLastStatusName());
                    m.put("lastStartDt", job.getLastStartDt());
                }
                list.add(m);
            }
        } catch (RuntimeException e) {
            log.warn("[System] 배치 목록 조회 실패: {}", e.getMessage());
        }
        return list;
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
