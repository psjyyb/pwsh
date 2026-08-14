package com.pwsh.domain.popup.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.popup.service.PopupService;
import com.pwsh.domain.popup.service.PopupVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팝업 관리 — 컨트롤러는 매핑만, 로직은 {@link PopupService}.
 * selectPopupList{variant}: ''=목록 / Main=사용자 메인 노출용 · updatePopup{variant}: ''=수정 / ordr=교환
 */
@RestController
@RequestMapping("/api/adm/popup")
@RequiredArgsConstructor
public class PopupController {

    private final PopupService popupService;

    /** 목록 계열: ''=페이징목록 / Main=사용자 메인 노출용(사용중+기간내) */
    @RequestMapping("/selectPopupList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) PopupVO searchVO) {
        PopupVO vo = searchVO == null ? new PopupVO() : searchVO;
        if ("Main".equals(variant)) {
            return ApiResponse.ok(popupService.selectMainList(vo));
        }
        int totalCount = popupService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", popupService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectPopupView.do")
    public ApiResponse<PopupVO> selectView(@RequestBody PopupVO searchVO) {
        return ApiResponse.ok(popupService.selectView(searchVO));
    }

    @RequestMapping("/insertPopup.do")
    public ApiResponse<Void> insert(@RequestBody PopupVO searchVO) {
        Validate.required(searchVO.getPopNm(), "팝업명");
        popupService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. variant: "ordr"=정렬 교환, 빈값=일반수정(+이미지 동기화) */
    @RequestMapping("/updatePopup{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody PopupVO searchVO) {
        if ("Ordr".equals(variant)) {
            popupService.swapOrdr(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getPopNm(), "팝업명");
            popupService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deletePopup.do")
    public ApiResponse<Void> delete(@RequestBody PopupVO searchVO) {
        popupService.delete(searchVO);
        return ApiResponse.ok();
    }
}
