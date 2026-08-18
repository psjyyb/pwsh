package com.pwsh.domain.follow.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.follow.service.FollowService;
import com.pwsh.domain.follow.service.FollowVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 팔로우 API — 매핑·입력검증만, 로직은 {@link FollowService}. 차단(BlockController)과 같은 형태.
 * 조회 {variant}: ''=내가 팔로우한 목록 / Followers=나를 팔로우한 목록 / Check=팔로우 여부 / Counts=팔로워·팔로잉 수(공개).
 */
@RestController
@RequestMapping("/api/adm/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/selectFollowList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable String variant, @RequestBody FollowVO vo) {
        if ("Followers".equals(variant)) {
            return ApiResponse.ok(followService.selectFollowerList());
        }
        if ("Check".equals(variant)) {
            Validate.required(vo.getFolloweeHandle(), "대상 회원");
            return ApiResponse.ok(followService.isFollowing(vo.getFolloweeHandle()) ? "Y" : "N");
        }
        if ("Counts".equals(variant)) {
            Validate.required(vo.getFolloweeHandle(), "대상 회원");
            return ApiResponse.ok(followService.selectCounts(vo.getFolloweeHandle()));
        }
        return ApiResponse.ok(followService.selectFollowingList());
    }

    /** 팔로우 토글(팔로우/해제) → {followedYn} */
    @PostMapping("/updateFollowToggle.do")
    public ApiResponse<FollowVO> toggle(@RequestBody FollowVO vo) {
        Validate.required(vo.getFolloweeHandle(), "대상 회원");
        return ApiResponse.ok(followService.toggle(vo.getFolloweeHandle()));
    }
}
