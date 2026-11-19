package com.pwsh.domain.search.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.search.service.SearchService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 검색 — 공개(SecurityConfig permitAll). 로직은 {@link SearchService}.
 */
@RestController
@RequestMapping("/api/adm/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 취미·모집·게시글·안내페이지 통합 검색.
     * body {filterKeyword} → {hobbies, recruits, posts, pages} + 유형별 총건수 + 네 유형 모두 0건이면 오타 교정 제안 {@code suggest}(CMS 1.2.39).
     *
     * <p>빈 검색어는 여기서 400으로 막는다(이 저장소의 기존 계약 — 회귀 방지 {@code EngagementTest}).
     * 서비스에도 같은 방어가 있지만 그쪽은 다른 호출자가 생겼을 때를 위한 것이다.
     */
    @RequestMapping("/selectSearchAll.do")
    public ApiResponse<Map<String, Object>> searchAll(@RequestBody Map<String, String> body) {
        String keyword = body.get("filterKeyword");
        Validate.required(keyword, "검색어");
        return ApiResponse.ok(searchService.searchAll(keyword));
    }

    /**
     * 인기 검색어(공개, CMS 1.2.38) — 최근 7일 2회 이상·결과가 있던 말, 금칙어·숨긴 말 제외, 많은 순 10개. 검색어 문자열만 싣는다.
     * 주소는 CMS와 같다(이 저장소의 통합검색만 selectSearchAll.do로 다르다).
     */
    @RequestMapping("/selectSearchListPopular.do")
    public ApiResponse<java.util.List<String>> popular() {
        return ApiResponse.ok(searchService.popular());
    }

    /**
     * 자동완성(공개, CMS 1.2.39) — 최근 30일 자주 검색되고 결과가 있던 말 중 입력이 든 말, 앞이 맞는 말 먼저 8개. 검색어 문자열만 싣는다.
     * 주소·요청 본문({keyword})은 CMS와 같다. 빈 입력은 400이 아니라 빈 목록 — 입력칸을 비우는 중에도 불린다.
     */
    @RequestMapping("/selectSearchListSuggest.do")
    public ApiResponse<java.util.List<String>> suggest(@RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(searchService.autocomplete(body == null ? null : body.get("keyword")));
    }
}
