package com.pwsh.domain.post.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.post.service.PostService;
import com.pwsh.domain.post.service.PostVO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 (사용자/관리자 공용, 권한 예외 — 로그인만 필요). 컨트롤러는 매핑만, 로직은 {@link PostService}.
 */
@RestController
@RequestMapping("/api/adm/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @RequestMapping("/selectPostList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody PostVO searchVO) {
        // 관리자 화면이 /adm/post/{boardId} 경로값을 그대로 보내므로 사용자가 URL을 손대면 문자열이 들어온다
        Validate.numeric(searchVO.getBoardId(), "게시판");
        int totalCount = postService.selectListTotalCount(searchVO);
        return ApiResponse.ok(Map.of(
                "list", postService.selectList(searchVO),
                "totalCount", totalCount,
                "page", PageUtil.of(searchVO.getPageNo(), searchVO.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectPostView.do")
    public ApiResponse<PostVO> selectView(@RequestBody PostVO searchVO) {
        return ApiResponse.ok(postService.selectView(searchVO));
    }

    /** 내가 쓴 글(마이페이지) — 로그인 본인 기준, 전 게시판. */
    @RequestMapping("/selectPostListMine.do")
    public ApiResponse<List<PostVO>> selectListMine() {
        return ApiResponse.ok(postService.selectListMine());
    }

    /** 주간 인기글(메인 '이번 주 베스트') — 공개(비로그인 포함). */
    @RequestMapping("/selectPostListWeeklyBest.do")
    public ApiResponse<List<PostVO>> selectListWeeklyBest() {
        return ApiResponse.ok(postService.selectListWeeklyBest());
    }

    /** 등록 후 생성된 게시글 ID 반환(첨부 매핑 연결용) */
    @RequestMapping("/insertPost.do")
    public ApiResponse<String> insert(@RequestBody PostVO searchVO) {
        Validate.required(searchVO.getBoardId(), "게시판");
        Validate.required(searchVO.getTitle(), "제목");
        postService.insert(searchVO);
        return ApiResponse.ok(searchVO.getRowId());
    }

    @RequestMapping("/updatePost.do")
    public ApiResponse<Void> update(@RequestBody PostVO searchVO) {
        Validate.required(searchVO.getTitle(), "제목");
        postService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deletePost.do")
    public ApiResponse<Void> delete(@RequestBody PostVO searchVO) {
        postService.delete(searchVO);
        return ApiResponse.ok();
    }
}
