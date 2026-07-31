package com.pwsh.domain.config.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.domain.config.service.ConfigService;
import com.pwsh.domain.config.service.ConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 환경설정 관리 (단일 행 — 조회/수정만). 컨트롤러는 매핑만, 로직은 {@link ConfigService}.
 * 표준 5메서드 중 목록/등록/삭제는 성격상 없음(단일 행).
 */
@RestController
@RequestMapping("/api/adm/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    /** 설정 조회 (단일 행) */
    @RequestMapping("/selectConfigView.do")
    public ApiResponse<ConfigVO> selectView() {
        return ApiResponse.ok(configService.selectView());
    }

    /** 설정 수정 */
    @RequestMapping("/updateConfig.do")
    public ApiResponse<Void> update(@RequestBody ConfigVO searchVO) {
        configService.update(searchVO);
        return ApiResponse.ok();
    }
}
