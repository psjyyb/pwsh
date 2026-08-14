package com.pwsh.domain.review.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.review.service.ReviewService;
import com.pwsh.domain.review.service.ReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모임 후기·평점 API — 매핑·입력검증만, 로직은 {@link ReviewService}.
 * 조회 {variant}: ''=회원이 받은 후기(공개), Stats=평균·건수(공개), Targets=내가 쓸 수 있는 대상(로그인).
 */
@RestController
@RequestMapping("/api/adm/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/selectReviewList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable String variant, @RequestBody ReviewVO vo) {
        if ("Stats".equals(variant)) {
            Validate.required(vo.getTargetHandle(), "대상 회원");
            return ApiResponse.ok(reviewService.selectStats(vo.getTargetHandle()));
        }
        if ("Targets".equals(variant)) {
            return ApiResponse.ok(reviewService.selectMyTargets());
        }
        Validate.required(vo.getTargetHandle(), "대상 회원");
        return ApiResponse.ok(reviewService.selectListByTarget(vo.getTargetHandle()));
    }

    /** 후기 등록 — 종료된 모임에서 함께한 회원에게만(서비스에서 자격·중복 검증). */
    @PostMapping("/insertReview.do")
    public ApiResponse<Void> insert(@RequestBody ReviewVO vo) {
        Validate.required(vo.getRecruitId(), "모임");
        Validate.required(vo.getTargetHandle(), "대상 회원");
        Validate.required(vo.getRating(), "별점");
        reviewService.insert(vo);
        return ApiResponse.ok();
    }

    /** 후기 삭제(논리) — 작성자·관리자 */
    @PostMapping("/deleteReview.do")
    public ApiResponse<Void> delete(@RequestBody ReviewVO vo) {
        Validate.required(vo.getRowId(), "후기");
        reviewService.delete(vo);
        return ApiResponse.ok();
    }
}
