package com.pwsh.domain.policy.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.policy.service.PolicyService;
import com.pwsh.domain.policy.service.PolicyVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 약관/정책 관리 — 컨트롤러는 매핑만, 로직은 {@link PolicyService}.
 * updatePolicy{variant}: ''=수정 / sortNo=정렬 교환
 */
@RestController
@RequestMapping("/api/adm/policy")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @RequestMapping("/selectPolicyList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) PolicyVO searchVO) {
        PolicyVO vo = searchVO == null ? new PolicyVO() : searchVO;
        int totalCount = policyService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", policyService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectPolicyView.do")
    public ApiResponse<PolicyVO> selectView(@RequestBody PolicyVO searchVO) {
        return ApiResponse.ok(policyService.selectView(searchVO));
    }

    @RequestMapping("/insertPolicy.do")
    public ApiResponse<Void> insert(@RequestBody PolicyVO searchVO) {
        Validate.required(searchVO.getTypeCd(), "약관유형");
        policyService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. variant: "sortNo"=정렬 교환, 빈값=일반수정 */
    @RequestMapping("/updatePolicy{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody PolicyVO searchVO) {
        if ("Sort".equals(variant)) {
            policyService.swapSort(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getTypeCd(), "약관유형");
            policyService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deletePolicy.do")
    public ApiResponse<Void> delete(@RequestBody PolicyVO searchVO) {
        policyService.delete(searchVO);
        return ApiResponse.ok();
    }
}
