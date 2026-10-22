package com.pwsh.domain.loginlog.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.domain.loginlog.service.LoginLogService;
import com.pwsh.domain.loginlog.service.LoginLogVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 이력 (조회 전용 — append-only). 기록은 AuthService가 로그인 시도마다 남긴다.
 * 컨트롤러는 매핑만, 로직은 {@link LoginLogService}.
 */
@RestController
@RequestMapping("/api/adm/loginlog")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @RequestMapping("/selectLoginLogList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) LoginLogVO searchVO) {
        LoginLogVO vo = searchVO == null ? new LoginLogVO() : searchVO;
        int totalCount = loginLogService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", loginLogService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }
}
