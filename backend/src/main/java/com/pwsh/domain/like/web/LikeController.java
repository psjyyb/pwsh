package com.pwsh.domain.like.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.like.service.LikeService;
import com.pwsh.domain.like.service.LikeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좋아요 토글 — 로그인 필요(본인 기준, 서버가 member_id 강제). 로직은 {@link LikeService}.
 */
@RestController
@RequestMapping("/api/adm/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /** 좋아요 토글. body {targetType: POST|COMMENT, targetId} → {likedYn, goodCnt} */
    @RequestMapping("/toggleLike.do")
    public ApiResponse<LikeVO> toggle(@RequestBody LikeVO vo) {
        Validate.required(vo.getTargetType(), "대상 유형");
        Validate.required(vo.getTargetId(), "대상");
        return ApiResponse.ok(likeService.toggle(vo.getTargetType(), vo.getTargetId()));
    }
}
