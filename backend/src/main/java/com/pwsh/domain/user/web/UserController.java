package com.pwsh.domain.user.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.PasswordPolicy;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.user.service.UserService;
import com.pwsh.domain.user.service.UserVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 관리 — 컨트롤러는 매핑·입력검증만, 로직은 {@link UserService}.
 * selectUserList{path}: ''=목록 / Authgrp=사용자의 권한그룹ID 목록
 * updateUser{path}: ''=정보수정 / Password=비번변경 / Authgrp=권한그룹 매핑 저장 / ForceLogout=강제 로그아웃
 */
@RestController
@RequestMapping("/api/adm/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 목록 계열: ''=페이징목록 / Authgrp=권한그룹ID 목록 */
    @RequestMapping("/selectUserList{path}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "path", required = false) String path,
                                     @RequestBody(required = false) UserVO searchVO) {
        UserVO vo = searchVO == null ? new UserVO() : searchVO;
        if ("Authgrp".equals(path)) {
            return ApiResponse.ok(userService.selectAuthgrpIds(vo));
        }
        int totCnt = userService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", userService.selectList(vo),
                "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    @RequestMapping("/selectUserView.do")
    public ApiResponse<UserVO> selectView(@RequestBody UserVO searchVO) {
        return ApiResponse.ok(userService.selectView(searchVO));
    }

    @RequestMapping("/insertUser.do")
    public ApiResponse<Void> insert(@RequestBody UserVO searchVO) {
        Validate.required(searchVO.getUserId(), "아이디");
        Validate.required(searchVO.getUserNm(), "이름");
        Validate.required(searchVO.getMemCd(), "회원유형");
        PasswordPolicy.validate(searchVO.getUserPw()); // 복잡도 정책(인코딩 전 원문)
        userService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. path: Password=비번변경 / Authgrp=권한그룹 매핑 / 빈값=정보수정 */
    @RequestMapping("/updateUser{path}.do")
    public ApiResponse<Void> update(@PathVariable(name = "path", required = false) String path,
                                    @RequestBody UserVO searchVO) {
        if ("Password".equals(path)) {
            PasswordPolicy.validate(searchVO.getUserPw());
            userService.updatePassword(searchVO);
        } else if ("Authgrp".equals(path)) {
            userService.saveAuthgrp(searchVO);
        } else if ("ForceLogout".equals(path)) {
            Validate.required(searchVO.getUserId(), "사용자");
            userService.forceLogout(searchVO);
        } else if ("Status".equals(path)) { // 제재: 정지(STATUS03)/해제(STATUS01)
            Validate.required(searchVO.getUserId(), "사용자");
            Validate.required(searchVO.getStatusCd(), "상태");
            userService.updateStatus(searchVO);
        } else if (StringUtil.isEmpty(path)) {
            Validate.required(searchVO.getUserNm(), "이름");
            Validate.required(searchVO.getMemCd(), "회원유형");
            userService.updateInfo(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteUser.do")
    public ApiResponse<Void> delete(@RequestBody UserVO searchVO) {
        userService.delete(searchVO);
        return ApiResponse.ok();
    }
}
