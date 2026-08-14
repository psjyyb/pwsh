package com.pwsh.domain.menu.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.menu.service.MenuService;
import com.pwsh.domain.menu.service.MenuVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메뉴 관리 — 컨트롤러는 매핑만, 로직은 {@link MenuService}.
 * selectMenuList{variant}: ''=목록 / Tree=사이드바(권한필터) / ManageTree=관리트리 · updateMenu{variant}: ''=수정 / ordr=교환
 */
@RestController
@RequestMapping("/api/adm/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** 목록 계열: ''=페이징목록 / Tree=사이드바 트리 / ManageTree=관리 트리 */
    @RequestMapping("/selectMenuList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) MenuVO searchVO) {
        MenuVO vo = searchVO == null ? new MenuVO() : searchVO;
        if ("Tree".equals(variant)) {
            return ApiResponse.ok(menuService.selectMenuTree(vo));
        }
        if ("ManageTree".equals(variant)) {
            return ApiResponse.ok(menuService.selectManageTree(vo));
        }
        int totalCount = menuService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", menuService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectMenuView.do")
    public ApiResponse<MenuVO> selectView(@RequestBody MenuVO searchVO) {
        return ApiResponse.ok(menuService.selectView(searchVO));
    }

    @RequestMapping("/insertMenu.do")
    public ApiResponse<Void> insert(@RequestBody MenuVO searchVO) {
        Validate.required(searchVO.getMenuNm(), "메뉴명");
        menuService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. variant: "ordr"=정렬 교환, 빈값=일반수정 */
    @RequestMapping("/updateMenu{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody MenuVO searchVO) {
        if ("Ordr".equals(variant)) {
            menuService.swapOrdr(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getMenuNm(), "메뉴명");
            menuService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteMenu.do")
    public ApiResponse<Void> delete(@RequestBody MenuVO searchVO) {
        menuService.delete(searchVO);
        return ApiResponse.ok();
    }
}
