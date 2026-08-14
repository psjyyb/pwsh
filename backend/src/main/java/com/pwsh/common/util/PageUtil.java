package com.pwsh.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 목록 응답의 페이징 정보 생성 헬퍼(REST/JSON 전용).
 * 서버는 totalPages/currentPage 같은 숫자만 내려주고, 페이지 링크 렌더링은 프론트가 담당한다.
 */
public final class PageUtil {

    private PageUtil() {
    }

    /**
     * @param pageIndex 현재 페이지(1-base)
     * @param size      페이지당 목록 수
     * @param totCnt    전체 건수
     * @return {currentPage, size, totalElements, totalPages}
     */
    public static Map<String, Object> of(int pageIndex, int size, int totCnt) {
        int safeSize = size <= 0 ? 10 : size;
        int totalPages = (int) Math.ceil((double) totCnt / safeSize);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("currentPage", pageIndex);
        page.put("size", safeSize);
        page.put("totalElements", totCnt);
        page.put("totalPages", totalPages);
        return page;
    }
}
