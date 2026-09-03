package com.pwsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.pwsh.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 내 근처 모집 찾기(거리 검색) 검증.
 * - 반경 안의 모집만 나오고, 가까운 순으로 정렬되는지
 * - 장소(좌표) 없는 모집은 거리 검색에서 빠지는지
 * - 좌표/반경을 일부만 보내면 400 (조건이 조용히 무시돼 "전국 목록"이 되는 것을 막는다)
 * - 좌표 범위·반경 상한을 넘기면 400
 * 기준점은 서울시청(37.5665, 126.9780). 실서버 + 실 PostgreSQL, 모킹 0.
 */
class RecruitNearbyTest extends IntegrationTest {

    private static final String BASE_LAT = "37.5665";
    private static final String BASE_LNG = "126.9780";

    @Test
    void nearby_filters_by_radius_and_orders_by_distance() throws Exception {
        String admin = accessToken("admin", "admin1234!");
        String hobbyId = JsonPath.read(post("/api/adm/hobby/insertHobby.do",
                "{\"hobbyName\":\"근처취미\",\"summary\":\"s\"}", admin).body(), "$.data");

        // 서울시청 기준: 광화문 ≈ 1.1km / 여의도 ≈ 8km / 수원 ≈ 33km / 좌표없음
        String near = insertRecruit(admin, hobbyId, "NEAR 광화문", "37.5759", "126.9769");
        String mid = insertRecruit(admin, hobbyId, "MID 여의도", "37.5219", "126.9245");
        String far = insertRecruit(admin, hobbyId, "FAR 수원", "37.2636", "127.0286");
        String noPlace = insertRecruit(admin, hobbyId, "NOPLACE 온라인", null, null);

        // 반경 5km: 광화문만
        List<String> r5 = titlesWithin("5");
        assertTrue(r5.contains("NEAR 광화문"), "1.1km 모집은 5km 안에 있어야 한다");
        assertFalse(r5.contains("MID 여의도"), "8km 모집은 5km 밖이라 빠져야 한다");
        assertFalse(r5.contains("FAR 수원"));
        assertFalse(r5.contains("NOPLACE 온라인"), "좌표 없는 모집은 거리 검색 대상이 아니다");

        // 반경 20km: 광화문 + 여의도, 가까운 순
        List<String> r20 = titlesWithin("20");
        assertTrue(r20.contains("NEAR 광화문") && r20.contains("MID 여의도"));
        assertFalse(r20.contains("FAR 수원"), "33km 모집은 20km 밖");
        assertTrue(r20.indexOf("NEAR 광화문") < r20.indexOf("MID 여의도"), "가까운 순으로 정렬돼야 한다");

        // 반경 50km: 셋 다
        List<String> r50 = titlesWithin("50");
        assertTrue(r50.contains("FAR 수원"));

        // 거리 값이 함께 내려오고, 실제 거리와 크게 다르지 않은지(하버사인 계산 검증)
        String body = nearBody("20");
        List<String> dists = JsonPath.read(body,
                "$.data.list[?(@.title=='NEAR 광화문')].distanceKm");
        double d = Double.parseDouble(dists.get(0));
        assertTrue(d > 0.5 && d < 2.0, "광화문까지는 약 1.1km 여야 한다 (실제: " + d + ")");

        // 거리 검색이 아니면 distanceKm 는 내려오지 않는다
        String plain = post("/api/adm/recruit/selectRecruitList.do",
                "{\"pageNo\":1,\"pageSize\":50}", null).body();
        assertFalse(plain.contains("distanceKm"), "전체 목록에는 거리 필드가 없다");

        // 총건수도 거리 조건을 반영해야 한다(목록과 페이징이 어긋나면 안 됨)
        int total5 = JsonPath.read(nearBody("5"), "$.data.totalCount");
        int total50 = JsonPath.read(nearBody("50"), "$.data.totalCount");
        assertTrue(total5 < total50, "반경이 넓어지면 총건수도 늘어야 한다");

        // 정리
        for (String id : List.of(near, mid, far, noPlace)) {
            post("/api/adm/recruit/deleteRecruit.do", "{\"rowId\":\"" + id + "\"}", admin);
        }
    }

    @Test
    void nearby_rejects_bad_input() throws Exception {
        // 반경만 / 좌표만 → 400 (조건이 무시되면 사용자는 근처인 줄 알고 전국 목록을 본다)
        assertEquals(400, nearStatus("{\"radiusKm\":\"5\"}"));
        assertEquals(400, nearStatus("{\"centerLat\":\"" + BASE_LAT + "\",\"centerLng\":\"" + BASE_LNG + "\"}"));
        // 좌표 범위 초과
        assertEquals(400, nearStatus("{\"centerLat\":\"99\",\"centerLng\":\"" + BASE_LNG + "\",\"radiusKm\":\"5\"}"));
        // 숫자 아님 (::numeric 캐스트 500 방지)
        assertEquals(400, nearStatus("{\"centerLat\":\"abc\",\"centerLng\":\"" + BASE_LNG + "\",\"radiusKm\":\"5\"}"));
        // 반경 0 / 상한 초과
        assertEquals(400, nearStatus("{\"centerLat\":\"" + BASE_LAT + "\",\"centerLng\":\"" + BASE_LNG + "\",\"radiusKm\":\"0\"}"));
        assertEquals(400, nearStatus("{\"centerLat\":\"" + BASE_LAT + "\",\"centerLng\":\"" + BASE_LNG + "\",\"radiusKm\":\"500\"}"));
    }

    private String insertRecruit(String token, String hobbyId, String title, String lat, String lng) throws Exception {
        String place = lat == null ? ""
                : ",\"placeName\":\"" + title + " 장소\",\"lat\":\"" + lat + "\",\"lng\":\"" + lng + "\"";
        return JsonPath.read(post("/api/adm/recruit/insertRecruit.do",
                "{\"hobbyId\":\"" + hobbyId + "\",\"title\":\"" + title + "\",\"content\":\"c\","
                        + "\"meetDt\":\"2026-12-01\"" + place + "}", token).body(), "$.data");
    }

    private String nearBody(String radiusKm) throws Exception {
        return post("/api/adm/recruit/selectRecruitList.do",
                "{\"centerLat\":\"" + BASE_LAT + "\",\"centerLng\":\"" + BASE_LNG + "\","
                        + "\"radiusKm\":\"" + radiusKm + "\",\"pageNo\":1,\"pageSize\":50}", null).body();
    }

    private List<String> titlesWithin(String radiusKm) throws Exception {
        return JsonPath.read(nearBody(radiusKm), "$.data.list[*].title");
    }

    private int nearStatus(String json) throws Exception {
        return post("/api/adm/recruit/selectRecruitList.do", json, null).statusCode();
    }
}
