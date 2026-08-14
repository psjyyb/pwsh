package com.pwsh.domain.authgrp.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.authgrp.service.AuthgrpService;
import com.pwsh.domain.authgrp.service.AuthgrpVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 권한그룹 관리 — 컨트롤러는 매핑만, 로직은 {@link AuthgrpService}.
 * selectAuthgrpList{variant}: ''=목록 / Combo=콤보 / Menu=그룹 메뉴권한 목록 / User=그룹 소속 사용자 목록
 * updateAuthgrp{variant}: ''=수정 / Menu=그룹-메뉴 매핑 저장 / User=그룹-사용자 매핑 저장
 */
@RestController
@RequestMapping("/api/adm/authgrp")
@RequiredArgsConstructor
public class AuthgrpController {

    private final AuthgrpService authgrpService;

    /** 목록 계열: ''=페이징목록 / Combo=콤보 / Menu=권한메뉴ID목록 / User=소속사용자ID목록 */
    @RequestMapping("/selectAuthgrpList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) AuthgrpVO searchVO) {
        AuthgrpVO vo = searchVO == null ? new AuthgrpVO() : searchVO;
        if ("Combo".equals(variant)) {
            return ApiResponse.ok(authgrpService.selectComboList(vo));
        }
        if ("Menu".equals(variant)) {
            return ApiResponse.ok(authgrpService.selectAuthMenuIds(vo));
        }
        if ("User".equals(variant)) {
            return ApiResponse.ok(authgrpService.selectUserIdsByGrp(vo));
        }
        int totCnt = authgrpService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", authgrpService.selectList(vo),
                "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    @RequestMapping("/selectAuthgrpView.do")
    public ApiResponse<AuthgrpVO> selectView(@RequestBody AuthgrpVO searchVO) {
        return ApiResponse.ok(authgrpService.selectView(searchVO));
    }

    @RequestMapping("/insertAuthgrp.do")
    public ApiResponse<Void> insert(@RequestBody AuthgrpVO searchVO) {
        Validate.required(searchVO.getRowId(), "권한그룹ID");
        Validate.required(searchVO.getAuthgrpNm(), "권한그룹명");
        authgrpService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. variant: Menu=그룹-메뉴 저장 / User=그룹-사용자 저장 / 빈값=일반수정 */
    @RequestMapping("/updateAuthgrp{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody AuthgrpVO searchVO) {
        if ("Menu".equals(variant)) {
            authgrpService.saveMenu(searchVO);
        } else if ("User".equals(variant)) {
            authgrpService.saveUser(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getAuthgrpNm(), "권한그룹명");
            authgrpService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteAuthgrp.do")
    public ApiResponse<Void> delete(@RequestBody AuthgrpVO searchVO) {
        authgrpService.delete(searchVO);
        return ApiResponse.ok();
    }
}
