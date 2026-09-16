package com.pwsh.domain.form.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.form.service.FormAnswerVO;
import com.pwsh.domain.form.service.FormService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 폼 응답 — 컨트롤러는 매핑만, 로직은 {@link FormService}(폼 도메인 단일 서비스).
 * selectFormAnswerList{variant}: ''=목록 / Stats=문항별 집계 / Export=내려받기용 전체 값
 * updateFormAnswer{variant}: Status=처리상태·메모
 *
 * <p>{@code insertFormAnswer}는 <b>사용자 제출</b>이다. 비로그인 제출을 허용하는 폼이 있으므로
 * permitAll로 열고, 로그인 필요·기간·중복·필수·선택지는 전부 서비스가 다시 본다.
 */
@RestController
@RequestMapping("/api/adm/formanswer")
@RequiredArgsConstructor
public class FormAnswerController {

    private final FormService formService;

    @RequestMapping("/selectFormAnswerList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) FormAnswerVO searchVO) {
        FormAnswerVO vo = searchVO == null ? new FormAnswerVO() : searchVO;
        if ("Stats".equals(variant)) {
            Validate.required(vo.getFormId(), "폼");
            return ApiResponse.ok(formService.stats(vo.getFormId()));
        }
        if ("Export".equals(variant)) {
            Validate.required(vo.getFormId(), "폼");
            return ApiResponse.ok(formService.export(vo.getFormId()));
        }
        int totalCount = formService.selectAnswerListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", formService.selectAnswerList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectFormAnswerView.do")
    public ApiResponse<FormAnswerVO> selectView(@RequestBody FormAnswerVO searchVO) {
        return ApiResponse.ok(formService.selectAnswerView(searchVO));
    }

    /** 사용자 제출. 반환값은 생성된 응답 ID. */
    @RequestMapping("/insertFormAnswer.do")
    public ApiResponse<String> insert(@RequestBody FormAnswerVO searchVO) {
        Validate.required(searchVO.getFormId(), "폼");
        return ApiResponse.ok(formService.submit(searchVO));
    }

    /**
     * 수정. variant: "Status"=처리상태·담당자 메모(관리자).
     * 빈 variant(응답 내용 수정)는 두지 않는다 — 제출된 답은 그대로 보존한다.
     */
    @RequestMapping("/updateFormAnswer{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody FormAnswerVO searchVO) {
        if ("Status".equals(variant)) {
            Validate.required(searchVO.getRowId(), "응답");
            Validate.required(searchVO.getStatusCd(), "처리상태");
            formService.updateAnswerStatus(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteFormAnswer.do")
    public ApiResponse<Void> delete(@RequestBody FormAnswerVO searchVO) {
        formService.deleteAnswer(searchVO);
        return ApiResponse.ok();
    }
}
