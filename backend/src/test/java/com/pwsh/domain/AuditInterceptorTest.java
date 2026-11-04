package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.code.service.CodeVO;
import com.pwsh.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 등록/수정 이력(audit) 자동 세팅 — 요청이 없는 실행에서의 IP.
 *
 * <p>왜 이 테스트가 있나. 스케줄 배치·기동 초기화처럼 HTTP 요청이 없는 경로는 요청 IP가 없다. 그때 reg_ip/upd_ip를
 * 비워 두면 대부분의 테이블이 이 두 컬럼을 NOT NULL로 둬서 <b>배치가 그런 테이블에 쓰는 순간 저장이 실패</b>한다
 * (이 저장소는 모집 하루 전 알림 배치를 넣을 때(`2f6b1d8`) 127.0.0.1로 채우게 했고, CMS도 1.2.29에서 같은 방식이 됐다).
 * 그래서 매퍼를 요청 없이(테스트 스레드에서) 직접 불러 배치 문맥을 재현한다.
 */
class AuditInterceptorTest extends IntegrationTest {

    @Autowired
    private CommonDAO commonDAO;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM code WHERE code_id LIKE 'ZZAUDITIP%'");
    }

    @Test
    @DisplayName("요청 없이 등록하면 IP가 127.0.0.1로 채워진다(NOT NULL 테이블도 저장된다)")
    void insertWithoutRequestFillsLocalIp() {
        commonDAO.insert("codeDAO.insert", code("ZZAUDITIP1"));

        Map<String, Object> row = row("ZZAUDITIP1");
        assertThat(row.get("reg_ip")).isEqualTo("127.0.0.1");
        assertThat(row.get("upd_ip")).isEqualTo("127.0.0.1");
        assertThat(row.get("reg_id")).as("로그인 문맥이 없으면 system").isEqualTo("system");
    }

    @Test
    @DisplayName("요청 없이 수정하면 upd_ip만 127.0.0.1로 채워지고 reg_ip는 그대로다")
    void updateWithoutRequestFillsLocalIp() {
        jdbc.update("INSERT INTO code (code_id, p_code_id, name, sort_no, use_yn, reg_id, upd_id, reg_dt, upd_dt, reg_ip, upd_ip)"
                + " VALUES ('ZZAUDITIP2', '0', '수정전', 1, 'Y', 'admin', 'admin', NOW(), NOW(), '10.0.0.1', '10.0.0.1')");
        CodeVO vo = code("ZZAUDITIP2");
        vo.setCodeName("수정후");
        commonDAO.update("codeDAO.update", vo);

        Map<String, Object> row = row("ZZAUDITIP2");
        assertThat(row.get("upd_ip")).isEqualTo("127.0.0.1");
        assertThat(row.get("reg_ip")).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("요청 없이도 호출부가 직접 넣은 IP는 덮어쓰지 않는다")
    void explicitIpIsKept() {
        // DataInitializer처럼 값을 직접 넣는 호출부가 있다 — 비었을 때만 채워야 한다
        CodeVO vo = code("ZZAUDITIP3");
        vo.setRegIp("10.9.9.9");
        vo.setUpdIp("10.9.9.9");
        commonDAO.insert("codeDAO.insert", vo);

        assertThat(row("ZZAUDITIP3").get("reg_ip")).isEqualTo("10.9.9.9");
    }

    @Test
    @DisplayName("요청이 있으면 기본값이 아니라 요청 IP가 남는다")
    void requestIpWins() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/adm/code/insertCode.do"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken("admin", "admin1234!"))
                .header("X-Forwarded-For", "10.20.30.40")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"rowId\":\"ZZAUDITIP4\",\"pCodeId\":\"0\",\"codeName\":\"요청IP\"}", StandardCharsets.UTF_8))
                .build();
        assertThat(http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

        assertThat(row("ZZAUDITIP4").get("reg_ip")).isEqualTo("10.20.30.40");
    }

    private CodeVO code(String id) {
        CodeVO vo = new CodeVO();
        vo.setRowId(id);
        vo.setPCodeId("0");
        vo.setCodeName("감사IP");
        return vo;
    }

    private Map<String, Object> row(String id) {
        return jdbc.queryForMap("SELECT reg_id, reg_ip, upd_ip FROM code WHERE code_id = ?", id);
    }
}
