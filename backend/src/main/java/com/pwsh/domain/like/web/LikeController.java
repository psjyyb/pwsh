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
 * 게시글·댓글 추천/비추천(권한 예외 — 로그인만 필요, 게시판 접근권은 서비스가 본다).
 * 컨트롤러는 매핑만, 로직은 {@link LikeService}.
 */
@RestController
@RequestMapping("/api/adm/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /** 투표(누르기·취소·전환). body {targetType: POST|COMMENT, targetId, likeType: GOOD|BAD} */
    @RequestMapping("/updateLike.do")
    public ApiResponse<LikeVO> update(@RequestBody LikeVO searchVO) {
        Validate.required(searchVO.getTargetType(), "대상 유형");
        Validate.required(searchVO.getTargetId(), "대상");
        return ApiResponse.ok(likeService.vote(searchVO));
    }
}
