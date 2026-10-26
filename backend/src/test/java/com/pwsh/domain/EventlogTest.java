package com.pwsh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwsh.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 활동로그 화면 데이터.
 *
 * <p>유형 이름은 코드 테이블을 조인해 내려준다. 조회 SQL의 별칭과 VO 필드 이름이 어긋나면 에러 없이
 * 값만 비고, 화면은 대신 코드값(LOGIN·INSERT…)을 보여준다 — 그래서 값 자체를 확인한다.
 */
class EventlogTest extends IntegrationTest {

    @Test
    @DisplayName("목록과 상세에 유형이 코드값이 아니라 한글 이름으로 나간다")
    void eventNameIsFilled() throws Exception {
        String admin = accessToken("admin", "admin1234!"); // 로그인 자체가 LOGIN 행을 남긴다

        String list = post("/api/adm/eventlog/selectEventlogList.do", "{\"eventCd\":\"LOGIN\"}", admin).body();
        assertThat((String) JsonPath.read(list, "$.data.list[0].eventName")).isEqualTo("로그인");

        String rowId = JsonPath.read(list, "$.data.list[0].rowId");
        String view = post("/api/adm/eventlog/selectEventlogView.do", "{\"rowId\":\"" + rowId + "\"}", admin).body();
        assertThat((String) JsonPath.read(view, "$.data.eventName")).isEqualTo("로그인");
    }
}
