package com.pwsh.domain.form.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.form.service.FormService;
import com.pwsh.domain.form.service.FormVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 폼 정의 관리(신청·민원·설문 공용) — 컨트롤러는 매핑만, 로직은 {@link FormService}.
 * selectFormList{variant}: ''=목록 / Combo=메뉴 연결 피커용
 *
 * <p>{@code selectFormView}는 <b>사용자 응답 화면도 쓴다</b>(폼+문항을 함께 내려준다).
 * 비로그인 접근은 SecurityConfig permitAll로 열고, 실제 열람 가부는 서비스가
 * {@code GenAccessGuard}로 판정한다(폼 ID 딥링크 차단).
 */
@RestController
@RequestMapping("/api/adm/form")
@RequiredArgsConstructor
public class FormController {

    private final FormService formService;

    @RequestMapping("/selectFormList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) FormVO searchVO) {
        FormVO vo = searchVO == null ? new FormVO() : searchVO;
        if ("Combo".equals(variant)) {
            return ApiResponse.ok(formService.selectListCombo(vo));
        }
        int totalCount = formService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", formService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    /** 폼 + 문항. 관리자 편집 화면과 사용자 응답 화면이 같이 쓴다. */
    @RequestMapping("/selectFormView.do")
    public ApiResponse<FormVO> selectView(@RequestBody FormVO searchVO) {
        return ApiResponse.ok(formService.selectView(searchVO));
    }

    @RequestMapping("/insertForm.do")
    public ApiResponse<Void> insert(@RequestBody FormVO searchVO) {
        validate(searchVO);
        formService.insert(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/updateForm{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody FormVO searchVO) {
        if (StringUtil.isEmpty(variant)) {
            validate(searchVO);
            formService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteForm.do")
    public ApiResponse<Void> delete(@RequestBody FormVO searchVO) {
        formService.delete(searchVO);
        return ApiResponse.ok();
    }

    private void validate(FormVO vo) {
        Validate.required(vo.getTitle(), "폼 제목");
        Validate.required(vo.getTypeCd(), "폼 유형");
    }
}
