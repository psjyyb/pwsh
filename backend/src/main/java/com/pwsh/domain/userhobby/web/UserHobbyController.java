package com.pwsh.domain.userhobby.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.userhobby.service.UserHobbyService;
import com.pwsh.domain.userhobby.service.UserHobbyVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원별 취미 레벨 — 본인 것만(로그인 필요). 로직은 {@link UserHobbyService}.
 */
@RestController
@RequestMapping("/api/adm/userHobby")
@RequiredArgsConstructor
public class UserHobbyController {

    private final UserHobbyService userHobbyService;

    @RequestMapping("/selectUserHobbyList.do")
    public ApiResponse<List<UserHobbyVO>> selectList() {
        return ApiResponse.ok(userHobbyService.selectMyList());
    }

    /** 내 취미 담기(관심) + 레벨(선택) upsert. 레벨 미지정=관심만, 지정=하는 중 레벨. */
    @RequestMapping("/insertUserHobby.do")
    public ApiResponse<Void> insert(@RequestBody UserHobbyVO searchVO) {
        Validate.required(searchVO.getHobbyId(), "취미");
        userHobbyService.saveLevel(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteUserHobby.do")
    public ApiResponse<Void> delete(@RequestBody UserHobbyVO searchVO) {
        Validate.required(searchVO.getHobbyId(), "취미");
        userHobbyService.deleteMy(searchVO);
        return ApiResponse.ok();
    }
}
