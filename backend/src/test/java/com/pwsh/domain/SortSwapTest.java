package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정렬 위/아래 교환(update{Name}Sort) 검증.
 *
 * <p>이 경로는 방향값(UP/DOWN)이 프론트 → 컨트롤러 → 서비스 → 매퍼 분기까지 흘러가야 동작한다.
 * 이름만 맞고 값이 전달되지 않으면 "호출은 200인데 순서는 그대로"인 조용한 실패가 되므로,
 * 상태코드가 아니라 <b>실제 sortNo 값이 뒤바뀌었는지</b>를 DB에서 확인한다.
 */
class SortSwapTest extends IntegrationTest {

    @Test
    @DisplayName("공통코드 순서 교환 — UP/DOWN이 실제 sortNo에 반영된다")
    void codeSortSwap() throws Exception {
        String admin = accessToken("admin", "admin1234!");

        // 같은 부모 아래 코드 2개 생성(sortNo는 서비스가 자동 부여)
        assertThat(post("/api/adm/code/insertCode.do",
                "{\"rowId\":\"ZZORD01\",\"pCodeId\":\"0\",\"codeNm\":\"순서A\"}", admin).statusCode()).isEqualTo(200);
        assertThat(post("/api/adm/code/insertCode.do",
                "{\"rowId\":\"ZZORD02\",\"pCodeId\":\"0\",\"codeNm\":\"순서B\"}", admin).statusCode()).isEqualTo(200);

        int a0 = sortOf("ZZORD01");
        int b0 = sortOf("ZZORD02");
        assertThat(b0).isGreaterThan(a0); // 나중에 만든 쪽이 뒤

        // 뒤에 있는 B를 위로 → A와 자리 교환
        assertThat(post("/api/adm/code/updateCodeSort.do",
                "{\"rowId\":\"ZZORD02\",\"direction\":\"UP\"}", admin).statusCode()).isEqualTo(200);
        assertThat(sortOf("ZZORD02")).isEqualTo(a0);
        assertThat(sortOf("ZZORD01")).isEqualTo(b0);

        // 다시 아래로 → 원위치
        assertThat(post("/api/adm/code/updateCodeSort.do",
                "{\"rowId\":\"ZZORD02\",\"direction\":\"DOWN\"}", admin).statusCode()).isEqualTo(200);
        assertThat(sortOf("ZZORD01")).isEqualTo(a0);
        assertThat(sortOf("ZZORD02")).isEqualTo(b0);

        post("/api/adm/code/deleteCode.do", "{\"rowId\":\"ZZORD01\"}", admin);
        post("/api/adm/code/deleteCode.do", "{\"rowId\":\"ZZORD02\"}", admin);
    }

    private int sortOf(String codeId) {
        Integer v = jdbc.queryForObject("SELECT sort_no FROM t_code WHERE code_id = ?", Integer.class, codeId);
        return v == null ? -1 : v;
    }
}
