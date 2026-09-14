package com.pwsh.domain.banner.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.banner.service.BannerService;
import com.pwsh.domain.banner.service.BannerVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배너 관리 — 컨트롤러는 매핑만, 로직은 {@link BannerService}.
 * selectBannerList{variant}: ''=목록 / Main=사용자 메인 노출용(비로그인 허용) · updateBanner{variant}: ''=수정 / Sort=교환
 */
@RestController
@RequestMapping("/api/adm/banner")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    /** 목록 계열: ''=페이징목록 / Main=사용자 메인 노출용(사용중+기간내) */
    @RequestMapping("/selectBannerList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) BannerVO searchVO) {
        BannerVO vo = searchVO == null ? new BannerVO() : searchVO;
        if ("Main".equals(variant)) {
            return ApiResponse.ok(bannerService.selectMainList(vo));
        }
        int totalCount = bannerService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", bannerService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectBannerView.do")
    public ApiResponse<BannerVO> selectView(@RequestBody BannerVO searchVO) {
        return ApiResponse.ok(bannerService.selectView(searchVO));
    }

    @RequestMapping("/insertBanner.do")
    public ApiResponse<Void> insert(@RequestBody BannerVO searchVO) {
        Validate.required(searchVO.getTitle(), "제목");
        bannerService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. variant: "Sort"=정렬 교환, 빈값=일반수정(+이미지 동기화) */
    @RequestMapping("/updateBanner{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody BannerVO searchVO) {
        if ("Sort".equals(variant)) {
            bannerService.swapSort(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getTitle(), "제목");
            bannerService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteBanner.do")
    public ApiResponse<Void> delete(@RequestBody BannerVO searchVO) {
        bannerService.delete(searchVO);
        return ApiResponse.ok();
    }
}
