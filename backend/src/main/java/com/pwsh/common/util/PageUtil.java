package com.pwsh.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 목록 응답의 페이징 정보 생성 헬퍼.
 * 표준 CMS CommonUtil.createMap의 page 블록을 REST(JSON) 전용으로 단순화.
 * (별도 프레임워크 PaginationInfo 대체 — 링크 계산은 프론트에서 totalPages/currentPage로 처리)
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
