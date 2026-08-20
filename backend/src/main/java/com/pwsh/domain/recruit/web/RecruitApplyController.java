package com.pwsh.domain.recruit.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.recruit.service.RecruitApplyVO;
import com.pwsh.domain.recruit.service.RecruitService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모집 참여 신청 (모집의 하위 엔티티, 댓글(CommentController) 패턴의 peer 컨트롤러).
 * 로직은 {@link RecruitService}(모집 도메인 단일 서비스)에 위임.
 * selectRecruitApplyList{variant}: ''=특정 모집 신청자 목록(주최자·관리자) / Mine=내 신청 내역.
 * insert=참여신청(회원), update=수락/거절(주최자·관리자), delete=신청취소(본인·관리자).
 */
@RestController
@RequestMapping("/api/adm/recruitApply")
@RequiredArgsConstructor
public class RecruitApplyController {

    private final RecruitService recruitService;

    @RequestMapping("/selectRecruitApplyList{variant}.do")
    public ApiResponse<List<RecruitApplyVO>> selectList(@PathVariable(name = "variant", required = false) String variant,
                                                        @RequestBody RecruitApplyVO searchVO) {
        if ("Mine".equals(variant)) {
            return ApiResponse.ok(recruitService.selectApplyListMine(searchVO));
        }
        Validate.required(searchVO.getRecruitId(), "모집");
        return ApiResponse.ok(recruitService.selectApplyList(searchVO));
    }

    @RequestMapping("/insertRecruitApply.do")
    public ApiResponse<Void> insert(@RequestBody RecruitApplyVO searchVO) {
        Validate.required(searchVO.getRecruitId(), "모집");
        recruitService.applyInsert(searchVO);
        return ApiResponse.ok();
    }

    /** update{variant}: ''=수락/거절 / Attend=참석 결과 기록(주최자·관리자, 모임 종료 후) */
    @RequestMapping("/updateRecruitApply{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody RecruitApplyVO searchVO) {
        if ("Attend".equals(variant)) {
            Validate.required(searchVO.getRowId(), "신청");
            recruitService.applyAttend(searchVO);
            return ApiResponse.ok();
        }
        Validate.required(searchVO.getApplyCd(), "신청상태");
        recruitService.applyUpdate(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteRecruitApply.do")
    public ApiResponse<Void> delete(@RequestBody RecruitApplyVO searchVO) {
        recruitService.applyDelete(searchVO);
        return ApiResponse.ok();
    }
}
