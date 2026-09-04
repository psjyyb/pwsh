package com.pwsh.domain.banword.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.banword.service.BanwordService;
import com.pwsh.domain.banword.service.BanwordVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 금칙어 관리 — 컨트롤러는 매핑·입력검증만, 로직은 {@link BanwordService}. */
@RestController
@RequestMapping("/api/adm/banword")
@RequiredArgsConstructor
public class BanwordController {

    private final BanwordService banwordService;

    @RequestMapping("/selectBanwordList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) BanwordVO searchVO) {
        BanwordVO vo = searchVO == null ? new BanwordVO() : searchVO;
        int totalCount = banwordService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", banwordService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectBanwordView.do")
    public ApiResponse<BanwordVO> selectView(@RequestBody BanwordVO searchVO) {
        return ApiResponse.ok(banwordService.selectView(searchVO));
    }

    @RequestMapping("/insertBanword.do")
    public ApiResponse<Void> insert(@RequestBody BanwordVO searchVO) {
        Validate.required(searchVO.getWord(), "금칙어");
        banwordService.insert(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/updateBanword.do")
    public ApiResponse<Void> update(@RequestBody BanwordVO searchVO) {
        Validate.required(searchVO.getWord(), "금칙어");
        banwordService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteBanword.do")
    public ApiResponse<Void> delete(@RequestBody BanwordVO searchVO) {
        banwordService.delete(searchVO);
        return ApiResponse.ok();
    }
}
