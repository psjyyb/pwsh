package com.pwsh.domain.accessip.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.accessip.service.AccessIpService;
import com.pwsh.domain.accessip.service.AccessIpVO;
import com.pwsh.global.web.ClientIpHolder;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 접속 허용 IP 관리 — 컨트롤러는 매핑·입력검증만, 로직은 {@link AccessIpService}. */
@RestController
@RequestMapping("/api/adm/accessip")
@RequiredArgsConstructor
public class AccessIpController {

    private final AccessIpService accessIpService;

    @RequestMapping("/selectAccessIpList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) AccessIpVO searchVO) {
        AccessIpVO vo = searchVO == null ? new AccessIpVO() : searchVO;
        int totalCount = accessIpService.selectListTotalCount(vo);
        // 현재 접속 IP와 제한 동작 여부를 함께 내려 화면에서 "내 IP 추가"·경고를 띄운다.
        Map<String, Object> data = new HashMap<>();
        data.put("list", accessIpService.selectList(vo));
        data.put("totalCount", totalCount);
        data.put("page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount));
        data.put("myIp", ClientIpHolder.get());
        data.put("enforcedYn", accessIpService.isEnforced() ? "Y" : "N");
        return ApiResponse.ok(data);
    }

    @RequestMapping("/selectAccessIpView.do")
    public ApiResponse<AccessIpVO> selectView(@RequestBody AccessIpVO searchVO) {
        return ApiResponse.ok(accessIpService.selectView(searchVO));
    }

    @RequestMapping("/insertAccessIp.do")
    public ApiResponse<Void> insert(@RequestBody AccessIpVO searchVO) {
        Validate.required(searchVO.getIp(), "IP");
        accessIpService.insert(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/updateAccessIp.do")
    public ApiResponse<Void> update(@RequestBody AccessIpVO searchVO) {
        Validate.required(searchVO.getIp(), "IP");
        accessIpService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteAccessIp.do")
    public ApiResponse<Void> delete(@RequestBody AccessIpVO searchVO) {
        accessIpService.delete(searchVO);
        return ApiResponse.ok();
    }
}
