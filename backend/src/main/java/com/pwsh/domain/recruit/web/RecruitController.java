package com.pwsh.domain.recruit.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.recruit.service.RecruitService;
import com.pwsh.domain.recruit.service.RecruitVO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모집(취미 모임원 모집). 컨트롤러는 매핑·입력검증만, 로직은 {@link RecruitService}.
 * 목록/상세는 공개(SecurityConfig permitAll), 등록/수정/삭제는 로그인(주최자·관리자).
 * updateRecruit{path}: ''=수정 / Status=모집상태 변경(마감/재개).
 * 참여 신청은 {@link RecruitApplyController}(peer, 댓글 패턴).
 */
@RestController
@RequestMapping("/api/adm/recruit")
@RequiredArgsConstructor
public class RecruitController {

    private final RecruitService recruitService;

    @RequestMapping("/selectRecruitList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody RecruitVO searchVO) {
        int totCnt = recruitService.selectListTotCnt(searchVO);
        return ApiResponse.ok(Map.of(
                "list", recruitService.selectList(searchVO),
                "totCnt", totCnt,
                "page", PageUtil.of(searchVO.getPageIndex(), searchVO.getSize(), totCnt)));
    }

    @RequestMapping("/selectRecruitView.do")
    public ApiResponse<RecruitVO> selectView(@RequestBody RecruitVO searchVO) {
        return ApiResponse.ok(recruitService.selectView(searchVO));
    }

    /** 내가 연 모집(마이페이지) — 로그인 본인 기준. */
    @RequestMapping("/selectRecruitListMine.do")
    public ApiResponse<List<RecruitVO>> selectListMine() {
        return ApiResponse.ok(recruitService.selectMyList());
    }

    /** 등록 후 생성된 모집 ID 반환. */
    @RequestMapping("/insertRecruit.do")
    public ApiResponse<String> insert(@RequestBody RecruitVO searchVO) {
        Validate.required(searchVO.getHobbyId(), "취미");
        Validate.required(searchVO.getTitle(), "모임명");
        recruitService.insert(searchVO);
        return ApiResponse.ok(searchVO.getDbKey());
    }

    @RequestMapping("/updateRecruit{path}.do")
    public ApiResponse<Void> update(@PathVariable(name = "path", required = false) String path,
                                    @RequestBody RecruitVO searchVO) {
        if ("Status".equals(path)) {
            recruitService.updateStatus(searchVO);
        } else {
            Validate.required(searchVO.getTitle(), "모임명");
            recruitService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteRecruit.do")
    public ApiResponse<Void> delete(@RequestBody RecruitVO searchVO) {
        recruitService.delete(searchVO);
        return ApiResponse.ok();
    }
}
