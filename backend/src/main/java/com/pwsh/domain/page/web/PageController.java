package com.pwsh.domain.page.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.page.service.PageService;
import com.pwsh.domain.page.service.PageVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 페이지(단일 콘텐츠) 관리 — 컨트롤러는 매핑만, 로직은 {@link PageService}. */
@RestController
@RequestMapping("/api/adm/page")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    @RequestMapping("/selectPageList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) PageVO searchVO) {
        PageVO vo = searchVO == null ? new PageVO() : searchVO;
        int totCnt = pageService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", pageService.selectList(vo),
                "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    @RequestMapping("/selectPageView.do")
    public ApiResponse<PageVO> selectView(@RequestBody PageVO searchVO) {
        return ApiResponse.ok(pageService.selectView(searchVO));
    }

    @RequestMapping("/insertPage.do")
    public ApiResponse<Void> insert(@RequestBody PageVO searchVO) {
        Validate.required(searchVO.getTitle(), "제목");
        Validate.required(searchVO.getContext(), "내용");
        pageService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. 빈값=일반수정 */
    @RequestMapping("/updatePage{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody PageVO searchVO) {
        if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getTitle(), "제목");
            Validate.required(searchVO.getContext(), "내용");
            pageService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deletePage.do")
    public ApiResponse<Void> delete(@RequestBody PageVO searchVO) {
        pageService.delete(searchVO);
        return ApiResponse.ok();
    }
}
