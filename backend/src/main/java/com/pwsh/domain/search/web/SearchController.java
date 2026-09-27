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
     * body {filterKeyword} → {hobbies, recruits, posts, pages} + 유형별 총건수.
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
}
