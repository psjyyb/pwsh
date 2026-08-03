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

    /** 취미·모집·게시글 통합 검색. body {searchKeyword} → {hobbies, recruits, posts} */
    @RequestMapping("/selectSearchAll.do")
    public ApiResponse<Map<String, Object>> searchAll(@RequestBody Map<String, String> body) {
        String keyword = body.get("searchKeyword");
        Validate.required(keyword, "검색어");
        return ApiResponse.ok(searchService.searchAll(keyword));
    }
}
