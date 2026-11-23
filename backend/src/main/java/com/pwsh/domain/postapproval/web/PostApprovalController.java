package com.pwsh.domain.postapproval.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.postapproval.service.PostApprovalService;
import com.pwsh.domain.postapproval.service.PostApprovalVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시 승인(관리자 > 게시판 관리 > 게시 승인, CMS 1.2.41) — 컨트롤러는 매핑·입력검증만, 로직은 {@link PostApprovalService}.
 * 권한은 메뉴(/adm/postapproval, 기본 ADMIN 그룹)로 PermissionInterceptor가 본다.
 * ⚠ 주소를 /api/adm/post/ 아래로 옮기지 않는다 — 그 경로는 사용자 게시판 콘텐츠라 메뉴 권한 검사에서 통째로 빠진다.
 */
@RestController
@RequestMapping("/api/adm/postapproval")
@RequiredArgsConstructor
public class PostApprovalController {

    private final PostApprovalService postApprovalService;

    @RequestMapping("/selectPostApprovalList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) PostApprovalVO searchVO) {
        PostApprovalVO vo = searchVO == null ? new PostApprovalVO() : searchVO;
        if (vo.getBoardId() != null && !vo.getBoardId().isEmpty()) {
            Validate.numeric(vo.getBoardId(), "게시판");
        }
        int totalCount = postApprovalService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", postApprovalService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectPostApprovalView.do")
    public ApiResponse<PostApprovalVO> selectView(@RequestBody PostApprovalVO searchVO) {
        Validate.required(searchVO.getRowId(), "게시글");
        Validate.numeric(searchVO.getRowId(), "게시글");
        return ApiResponse.ok(postApprovalService.selectView(searchVO));
    }

    /** 승인·반려 — {rowId, approvalCd: APPROVAL02(승인)|APPROVAL03(반려), approvalMemo(반려 사유)} */
    @RequestMapping("/updatePostApproval.do")
    public ApiResponse<Void> update(@RequestBody PostApprovalVO searchVO) {
        Validate.required(searchVO.getRowId(), "게시글");
        Validate.numeric(searchVO.getRowId(), "게시글");
        Validate.required(searchVO.getApprovalCd(), "처리");
        postApprovalService.update(searchVO);
        return ApiResponse.ok();
    }
}
