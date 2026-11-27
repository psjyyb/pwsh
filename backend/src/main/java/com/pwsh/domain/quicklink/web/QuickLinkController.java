package com.pwsh.domain.quicklink.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.quicklink.service.QuickLinkService;
import com.pwsh.domain.quicklink.service.QuickLinkVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자주찾는 서비스(CMS 1.2.45) — 컨트롤러는 매핑만, 로직은 {@link QuickLinkService}.
 * selectQuickLinkList{variant}: ''=관리 목록 / Main=사용자 메인(비로그인 허용 — SecurityConfig permitAll + PermissionInterceptor 예외)
 * · updateQuickLink{variant}: ''=수정 / Sort=인접 교환. 관리 기능의 권한은 메뉴(/adm/quicklink, 기본 ADMIN 그룹)로 PermissionInterceptor가 본다.
 */
@RestController
@RequestMapping("/api/adm/quicklink")
@RequiredArgsConstructor
public class QuickLinkController {

    private final QuickLinkService quickLinkService;

    @RequestMapping("/selectQuickLinkList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) QuickLinkVO searchVO) {
        if ("Main".equals(variant)) {
            return ApiResponse.ok(quickLinkService.selectMainList());
        }
        QuickLinkVO vo = searchVO == null ? new QuickLinkVO() : searchVO;
        int totalCount = quickLinkService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", quickLinkService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectQuickLinkView.do")
    public ApiResponse<QuickLinkVO> selectView(@RequestBody QuickLinkVO searchVO) {
        Validate.numeric(searchVO.getRowId(), "자주찾는 서비스");
        return ApiResponse.ok(quickLinkService.selectView(searchVO));
    }

    @RequestMapping("/insertQuickLink.do")
    public ApiResponse<String> insert(@RequestBody QuickLinkVO searchVO) {
        Validate.required(searchVO.getName(), "이름");
        Validate.required(searchVO.getLinkUrl(), "주소");
        return ApiResponse.ok(quickLinkService.insert(searchVO));
    }

    @RequestMapping("/updateQuickLink{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody QuickLinkVO searchVO) {
        Validate.required(searchVO.getRowId(), "자주찾는 서비스");
        Validate.numeric(searchVO.getRowId(), "자주찾는 서비스");
        if ("Sort".equals(variant)) {
            quickLinkService.swapSort(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getName(), "이름");
            Validate.required(searchVO.getLinkUrl(), "주소");
            quickLinkService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteQuickLink.do")
    public ApiResponse<Void> delete(@RequestBody QuickLinkVO searchVO) {
        Validate.required(searchVO.getRowId(), "자주찾는 서비스");
        Validate.numeric(searchVO.getRowId(), "자주찾는 서비스");
        quickLinkService.delete(searchVO);
        return ApiResponse.ok();
    }
}
