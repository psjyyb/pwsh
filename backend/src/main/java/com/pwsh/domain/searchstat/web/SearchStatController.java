package com.pwsh.domain.searchstat.web;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.domain.searchstat.service.SearchStatService;
import com.pwsh.domain.searchstat.service.SearchStatVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 통계(관리자 > 시스템관리 > 검색 통계, 1.2.38) — 컨트롤러는 매핑만, 로직은 {@link SearchStatService}.
 * selectSearchStatList{variant}: ''=검색어 순위(페이징) / Daily=일별 합계
 * updateSearchStat{variant}: Hide=인기 검색어에서 숨김·해제
 * 권한은 메뉴(/adm/searchstat, 기본 ADMIN 그룹)로 PermissionInterceptor가 본다.
 */
@RestController
@RequestMapping("/api/adm/searchstat")
@RequiredArgsConstructor
public class SearchStatController {

    private final SearchStatService searchStatService;

    @RequestMapping("/selectSearchStatList{variant}.do")
    public ApiResponse<Map<String, Object>> selectList(@PathVariable(name = "variant", required = false) String variant,
                                                       @RequestBody(required = false) SearchStatVO searchVO) {
        SearchStatVO vo = searchVO == null ? new SearchStatVO() : searchVO;
        if ("Daily".equals(variant)) {
            return ApiResponse.ok(Map.of("list", searchStatService.selectDaily(vo)));
        }
        int totalCount = searchStatService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", searchStatService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/updateSearchStat{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody SearchStatVO searchVO) {
        // 집계 행은 검색이 만들고 고치지 않는다 — 바꿀 수 있는 건 숨김뿐이다
        if (!"Hide".equals(variant)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.validate.invalid", "변경 유형"));
        }
        searchStatService.hide(searchVO);
        return ApiResponse.ok();
    }
}
