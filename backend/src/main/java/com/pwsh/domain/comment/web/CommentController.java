package com.pwsh.domain.comment.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.comment.service.CommentService;
import com.pwsh.domain.comment.service.CommentVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 댓글 (게시글 하위, 권한 예외 — 로그인만 필요). 컨트롤러는 매핑만, 로직은 {@link CommentService}. */
@RestController
@RequestMapping("/api/adm/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @RequestMapping("/selectCommentList.do")
    public ApiResponse<List<CommentVO>> selectList(@RequestBody CommentVO searchVO) {
        return ApiResponse.ok(commentService.selectList(searchVO));
    }

    @RequestMapping("/insertComment.do")
    public ApiResponse<Void> insert(@RequestBody CommentVO searchVO) {
        Validate.required(searchVO.getPostId(), "게시글");
        Validate.required(searchVO.getContext(), "내용");
        commentService.insert(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/updateComment.do")
    public ApiResponse<Void> update(@RequestBody CommentVO searchVO) {
        Validate.required(searchVO.getContext(), "내용");
        commentService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteComment.do")
    public ApiResponse<Void> delete(@RequestBody CommentVO searchVO) {
        commentService.delete(searchVO);
        return ApiResponse.ok();
    }
}
