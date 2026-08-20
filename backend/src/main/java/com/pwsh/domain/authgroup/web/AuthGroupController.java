package com.pwsh.domain.authgroup.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.authgroup.service.AuthGroupService;
import com.pwsh.domain.authgroup.service.AuthGroupVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 권한그룹 관리 — 컨트롤러는 매핑만, 로직은 {@link AuthGroupService}.
 * selectAuthGroupList{variant}: ''=목록 / Combo=콤보 / Menu=그룹 메뉴권한 목록 / User=그룹 소속 사용자 목록
 * updateAuthGroup{variant}: ''=수정 / Menu=그룹-메뉴 매핑 저장 / User=그룹-사용자 매핑 저장
 */
@RestController
@RequestMapping("/api/adm/authgroup")
@RequiredArgsConstructor
public class AuthGroupController {

    private final AuthGroupService authGroupService;

    /** 목록 계열: ''=페이징목록 / Combo=콤보 / Menu=권한메뉴ID목록 / User=소속사용자ID목록 */
    @RequestMapping("/selectAuthGroupList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) AuthGroupVO searchVO) {
        AuthGroupVO vo = searchVO == null ? new AuthGroupVO() : searchVO;
        if ("Combo".equals(variant)) {
            return ApiResponse.ok(authGroupService.selectComboList(vo));
        }
        if ("Menu".equals(variant)) {
            return ApiResponse.ok(authGroupService.selectAuthMenuIds(vo));
        }
        if ("Member".equals(variant)) {
            return ApiResponse.ok(authGroupService.selectMemberIdsByGroup(vo));
        }
        int totalCount = authGroupService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", authGroupService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectAuthGroupView.do")
    public ApiResponse<AuthGroupVO> selectView(@RequestBody AuthGroupVO searchVO) {
        return ApiResponse.ok(authGroupService.selectView(searchVO));
    }

    @RequestMapping("/insertAuthGroup.do")
    public ApiResponse<Void> insert(@RequestBody AuthGroupVO searchVO) {
        Validate.required(searchVO.getRowId(), "권한그룹ID");
        Validate.required(searchVO.getAuthGroupName(), "권한그룹명");
        authGroupService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. variant: Menu=그룹-메뉴 저장 / User=그룹-사용자 저장 / 빈값=일반수정 */
    @RequestMapping("/updateAuthGroup{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody AuthGroupVO searchVO) {
        if ("Menu".equals(variant)) {
            authGroupService.saveMenu(searchVO);
        } else if ("Member".equals(variant)) {
            authGroupService.saveMember(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getAuthGroupName(), "권한그룹명");
            authGroupService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteAuthGroup.do")
    public ApiResponse<Void> delete(@RequestBody AuthGroupVO searchVO) {
        authGroupService.delete(searchVO);
        return ApiResponse.ok();
    }
}
