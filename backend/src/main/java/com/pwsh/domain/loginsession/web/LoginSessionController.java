package com.pwsh.domain.loginsession.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.loginsession.service.LoginSessionService;
import com.pwsh.domain.loginsession.service.LoginSessionVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 접속 세션 관리 — 컨트롤러는 매핑·입력검증만, 로직은 {@link LoginSessionService}.
 *
 * <p>세션은 로그인으로만 생기고 로그아웃·강제종료로만 끝나므로 insert/delete 엔드포인트는 두지 않는다
 * (event_log와 같은 이유). 변경 동작은 {@code updateLoginSessionForceEnd.do} 하나뿐이다.
 */
@RestController
@RequestMapping("/api/adm/loginsession")
@RequiredArgsConstructor
public class LoginSessionController {

    private final LoginSessionService loginSessionService;

    @RequestMapping("/selectLoginSessionList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) LoginSessionVO searchVO) {
        LoginSessionVO vo = searchVO == null ? new LoginSessionVO() : searchVO;
        int totalCount = loginSessionService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", loginSessionService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectLoginSessionView.do")
    public ApiResponse<LoginSessionVO> selectView(@RequestBody LoginSessionVO searchVO) {
        return ApiResponse.ok(loginSessionService.selectView(searchVO));
    }

    /** updateLoginSession{variant}: ForceEnd=강제 종료(세션 닫기 + 토큰 무효화) */
    @RequestMapping("/updateLoginSession{variant}.do")
    public ApiResponse<Void> update(@PathVariable String variant, @RequestBody LoginSessionVO searchVO) {
        if ("ForceEnd".equals(variant)) {
            Validate.required(searchVO.getRowId(), "세션");
            loginSessionService.forceEnd(searchVO);
        }
        return ApiResponse.ok();
    }
}
