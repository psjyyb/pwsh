package com.pwsh.domain.searchsynonym.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.searchsynonym.service.SearchSynonymService;
import com.pwsh.domain.searchsynonym.service.SearchSynonymVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 동의어(관리자 > 시스템관리 > 검색 동의어, CMS 1.2.39) — 컨트롤러는 매핑·입력검증만, 로직은 {@link SearchSynonymService}.
 * 권한은 메뉴(/adm/searchsynonym, 기본 ADMIN 그룹)로 PermissionInterceptor가 본다.
 */
@RestController
@RequestMapping("/api/adm/searchsynonym")
@RequiredArgsConstructor
public class SearchSynonymController {

    private final SearchSynonymService searchSynonymService;

    @RequestMapping("/selectSearchSynonymList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) SearchSynonymVO searchVO) {
        SearchSynonymVO vo = searchVO == null ? new SearchSynonymVO() : searchVO;
        int totalCount = searchSynonymService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", searchSynonymService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectSearchSynonymView.do")
    public ApiResponse<SearchSynonymVO> selectView(@RequestBody SearchSynonymVO searchVO) {
        Validate.numeric(searchVO.getRowId(), "동의어 묶음");
        return ApiResponse.ok(searchSynonymService.selectView(searchVO));
    }

    @RequestMapping("/insertSearchSynonym.do")
    public ApiResponse<Void> insert(@RequestBody SearchSynonymVO searchVO) {
        Validate.required(searchVO.getWords(), "동의어");
        searchSynonymService.insert(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/updateSearchSynonym.do")
    public ApiResponse<Void> update(@RequestBody SearchSynonymVO searchVO) {
        Validate.required(searchVO.getRowId(), "동의어 묶음");
        Validate.numeric(searchVO.getRowId(), "동의어 묶음");
        Validate.required(searchVO.getWords(), "동의어");
        searchSynonymService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteSearchSynonym.do")
    public ApiResponse<Void> delete(@RequestBody SearchSynonymVO searchVO) {
        Validate.required(searchVO.getRowId(), "동의어 묶음");
        Validate.numeric(searchVO.getRowId(), "동의어 묶음");
        searchSynonymService.delete(searchVO);
        return ApiResponse.ok();
    }
}
