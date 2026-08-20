package com.pwsh.support;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합테스트 공통 베이스. 실제 임베디드 서버(RANDOM_PORT) + 실제 PostgreSQL(pwsh_test) + 실제 HTTP.
 * - 모킹 0: 컨트롤러→서비스→매퍼→DB 전 구간을 실제 HTTP로 태운다. (Boot 4.1 MockMvc 모듈 의존 회피)
 * - JSON 파싱은 JsonPath(Boot 4.1은 Jackson 3라 ObjectMapper 직접 사용 회피).
 * - 기본 시드 계정: admin/admin1234!(MEM02), user/user1234!(MEM01). 스키마·시드는 프로파일 test가 매 실행 초기화.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Value("${local.server.port}")
    protected int port;

    /** 실 DB 직접 조작용(테스트 전제 데이터 준비). 프로덕션 경로는 항상 HTTP로 태운다. */
    @Autowired
    protected JdbcTemplate jdbc;

    protected final HttpClient http = HttpClient.newHttpClient();

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** POST(JSON). token=null이면 비로그인(익명). */
    protected HttpResponse<String> post(String path, String jsonBody, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody, StandardCharsets.UTF_8));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** GET 요청(다운로드·공개이미지 등). token=null이면 비로그인. */
    protected HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 로그인 요청(성공/실패 모두 반환 — 상태코드 검증용). */
    protected HttpResponse<String> login(String memberId, String password) throws Exception {
        return post("/api/auth/login",
                "{\"memberId\":\"" + memberId + "\",\"password\":\"" + password + "\"}", null);
    }

    /** 로그인 후 access 토큰 추출(성공 전제). */
    protected String accessToken(String memberId, String password) throws Exception {
        return JsonPath.read(login(memberId, password).body(), "$.data.accessToken");
    }

    /**
     * 셀프 회원가입(이메일 인증 포함). 실제 메일 발송 없이 검증 경로를 그대로 태우기 위해
     * email_verification에 유효 코드를 직접 심고(테스트 전제 데이터) /api/auth/signup을 호출한다.
     * (SMTP 발송은 EmailVerifyService.issue 경로이며, 여기서는 검증(verify) 경로를 테스트한다)
     */
    protected HttpResponse<String> signup(String memberId, String nickname, String email) throws Exception {
        String code = "123456";
        jdbc.update("DELETE FROM email_verification WHERE target = ? AND purpose = 'SIGNUP'", email);
        jdbc.update("INSERT INTO email_verification (target, purpose, code, expire_dt) VALUES (?, 'SIGNUP', ?, NOW() + INTERVAL '10 minutes')",
                email, code);
        return post("/api/auth/signup",
                "{\"memberId\":\"" + memberId + "\",\"password\":\"Test1234!@\",\"pwConfirm\":\"Test1234!@\","
                        + "\"nickname\":\"" + nickname + "\",\"email\":\"" + email + "\",\"code\":\"" + code + "\"}",
                null);
    }

    /** 테스트용 회원(MEM01) 생성. 비번은 정책 충족값(Test1234!@). 관리자 토큰 필요. */
    protected HttpResponse<String> createMember(String adminToken, String memberId) throws Exception {
        return post("/api/adm/member/insertMember.do",
                "{\"memberId\":\"" + memberId + "\",\"memberName\":\"테스트\",\"typeCd\":\"MEM01\","
                        + "\"statusCd\":\"STATUS01\",\"password\":\"Test1234!@\"}",
                adminToken);
    }
}
