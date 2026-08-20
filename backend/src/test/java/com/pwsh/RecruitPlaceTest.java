package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 모집 장소(지도) 검증 — 장소명·주소·좌표 저장/조회, 다음 회차 복제 시 승계, 좌표 입력 방어.
 *
 * <p>지도 렌더링은 브라우저(카카오 SDK) 몫이라 여기서 검증하지 않는다. 서버가 지켜야 하는 건
 * "화면이 마커를 찍을 수 있는 값이 정확히 오가는가"와 "이상한 좌표를 걸러내는가" 두 가지다.
 */
class RecruitPlaceTest extends IntegrationTest {

    private static final String LAT = "37.4979502";  // 강남역
    private static final String LNG = "127.0276368";

    @Test
    void place_is_saved_and_returned() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"장소취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        assertNotNull(hobbyId);

        String future = LocalDate.now().plusDays(5).toString();
        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"장소 지정 모집\",\"meetDt\":\"" + future + "\","
                        + "\"areaCd\":\"AREA01\",\"placeName\":\"강남역 11번 출구\","
                        + "\"addr\":\"서울 강남구 강남대로 396\",\"lat\":\"" + LAT + "\",\"lng\":\"" + LNG + "\"}",
                admin).body(), "$.data");
        assertNotNull(rid);

        String view = post("/api/adm/recruit/selectRecruitView.do", "{\"rowId\":\"" + rid + "\"}", null).body();
        assertEquals("강남역 11번 출구", JsonPath.read(view, "$.data.placeName"));
        assertEquals("서울 강남구 강남대로 396", JsonPath.read(view, "$.data.addr"));
        // 좌표는 NUMERIC이라 DB가 정규화한다 — 문자열 동등비교 대신 값으로 확인
        assertEquals(Double.parseDouble(LAT), Double.parseDouble(JsonPath.read(view, "$.data.lat")), 0.0000001);
        assertEquals(Double.parseDouble(LNG), Double.parseDouble(JsonPath.read(view, "$.data.lng")), 0.0000001);

        // 목록에는 장소명이 실린다(카드/표 표시용). 좌표는 상세에서만 쓴다.
        String list = post("/api/adm/recruit/selectRecruitList.do",
                "{\"pageNo\":1,\"pageSize\":50,\"hobbyId\":\"" + hobbyId + "\"}", null).body();
        assertTrue(list.contains("강남역 11번 출구"), "목록에 장소명 노출");

        // 장소를 지우면(빈 값) NULL로 되돌아간다 — 온라인·장소 미정 모임
        assertEquals(200, post("/api/adm/recruit/updateRecruit.do",
                "{\"rowId\":\"" + rid + "\",\"title\":\"장소 지정 모집\",\"placeName\":\"\",\"addr\":\"\","
                        + "\"lat\":\"\",\"lng\":\"\"}", admin).statusCode());
        // 널 필드는 응답 JSON에서 빠지므로(직렬화 설정) 본문 미포함 + DB NULL 두 가지로 확인한다
        String cleared = post("/api/adm/recruit/selectRecruitView.do", "{\"rowId\":\"" + rid + "\"}", null).body();
        assertTrue(!cleared.contains("placeName"), "해제 후에는 장소명이 응답에 없다");
        assertNull(jdbc.queryForObject("SELECT place_name FROM recruit WHERE recruit_id = ?::integer",
                String.class, rid));
        assertNull(jdbc.queryForObject("SELECT lat::text FROM recruit WHERE recruit_id = ?::integer",
                String.class, rid));
    }

    @Test
    void copy_carries_place_to_next_round() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"장소복제취미\",\"summary\":\"s\"}", admin).body(), "$.data");

        String rid = JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"정기 모임\",\"meetDt\":\""
                        + LocalDate.now().plusDays(3) + "\",\"placeName\":\"강남역 11번 출구\","
                        + "\"addr\":\"서울 강남구 강남대로 396\",\"lat\":\"" + LAT + "\",\"lng\":\"" + LNG + "\"}",
                admin).body(), "$.data");

        // 일정만 바꿔 다음 회차 — 같은 곳에서 모이므로 장소·좌표가 따라와야 한다
        String nextId = JsonPath.read(post("/api/adm/recruit/insertRecruitCopy.do",
                "{\"rowId\":\"" + rid + "\",\"meetDt\":\"" + LocalDate.now().plusDays(10) + "\"}",
                admin).body(), "$.data");
        String next = post("/api/adm/recruit/selectRecruitView.do", "{\"rowId\":\"" + nextId + "\"}", null).body();
        assertEquals("강남역 11번 출구", JsonPath.read(next, "$.data.placeName"));
        assertEquals(Double.parseDouble(LAT), Double.parseDouble(JsonPath.read(next, "$.data.lat")), 0.0000001);
    }

    @Test
    void bad_coordinates_are_rejected_with_400() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"좌표검증취미\",\"summary\":\"s\"}", admin).body(), "$.data");
        String base = "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"좌표검증\",";

        // 숫자가 아닌 좌표 — 매퍼의 ::numeric 캐스트가 500으로 터지지 않아야 한다
        assertEquals(400, post("/api/adm/recruit/insertRecruit.do",
                base + "\"lat\":\"서울\",\"lng\":\"강남\"}", admin).statusCode());
        // 범위를 벗어난 좌표
        assertEquals(400, post("/api/adm/recruit/insertRecruit.do",
                base + "\"lat\":\"99.9\",\"lng\":\"127.0\"}", admin).statusCode());
        assertEquals(400, post("/api/adm/recruit/insertRecruit.do",
                base + "\"lat\":\"37.5\",\"lng\":\"200.0\"}", admin).statusCode());
        // 위도만 오면 마커를 찍을 수 없다
        assertEquals(400, post("/api/adm/recruit/insertRecruit.do",
                base + "\"lat\":\"37.5\"}", admin).statusCode());
        // 좌표 없이(장소 미정) 등록은 정상
        assertEquals(200, post("/api/adm/recruit/insertRecruit.do", base + "\"meetDt\":\"\"}", admin).statusCode());
    }
}
