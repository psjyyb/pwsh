package com.pwsh.support;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
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
    protected HttpResponse<String> login(String userId, String userPw) throws Exception {
        return post("/api/auth/login",
                "{\"userId\":\"" + userId + "\",\"userPw\":\"" + userPw + "\"}", null);
    }

    /** 로그인 후 access 토큰 추출(성공 전제). */
    protected String accessToken(String userId, String userPw) throws Exception {
        return JsonPath.read(login(userId, userPw).body(), "$.data.accessToken");
    }

    /** 테스트용 회원(MEM01) 생성. 비번은 정책 충족값(Test1234!@). 관리자 토큰 필요. */
    protected HttpResponse<String> createMember(String adminToken, String userId) throws Exception {
        return post("/api/adm/user/insertUser.do",
                "{\"userId\":\"" + userId + "\",\"userNm\":\"테스트\",\"memCd\":\"MEM01\","
                        + "\"statusCd\":\"STATUS01\",\"userPw\":\"Test1234!@\"}",
                adminToken);
    }
}
