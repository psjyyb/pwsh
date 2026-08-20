package com.pwsh.domain.memberhobby.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.memberhobby.service.MemberHobbyService;
import com.pwsh.domain.memberhobby.service.MemberHobbyVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원별 취미 레벨 — 본인 것만(로그인 필요). 로직은 {@link MemberHobbyService}.
 */
@RestController
@RequestMapping("/api/adm/memberHobby")
@RequiredArgsConstructor
public class MemberHobbyController {

    private final MemberHobbyService memberHobbyService;

    @RequestMapping("/selectMemberHobbyList.do")
    public ApiResponse<List<MemberHobbyVO>> selectList() {
        return ApiResponse.ok(memberHobbyService.selectMyList());
    }

    /** 내 취미 담기(관심) + 레벨(선택) upsert. 레벨 미지정=관심만, 지정=하는 중 레벨. */
    @RequestMapping("/insertMemberHobby.do")
    public ApiResponse<Void> insert(@RequestBody MemberHobbyVO searchVO) {
        Validate.required(searchVO.getHobbyId(), "취미");
        memberHobbyService.saveLevel(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteMemberHobby.do")
    public ApiResponse<Void> delete(@RequestBody MemberHobbyVO searchVO) {
        Validate.required(searchVO.getHobbyId(), "취미");
        memberHobbyService.deleteMy(searchVO);
        return ApiResponse.ok();
    }
}
