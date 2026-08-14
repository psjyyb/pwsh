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
     * @param pageNo 현재 페이지(1-base)
     * @param pageSize      페이지당 목록 수
     * @param totalCount    전체 건수
     * @return {currentPage, pageSize, totalElements, totalPages}
     */
    public static Map<String, Object> of(int pageNo, int pageSize, int totalCount) {
        int safeSize = pageSize <= 0 ? 10 : pageSize;
        int totalPages = (int) Math.ceil((double) totalCount / safeSize);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("currentPage", pageNo);
        page.put("pageSize", safeSize);
        page.put("totalElements", totalCount);
        page.put("totalPages", totalPages);
        return page;
    }
}
