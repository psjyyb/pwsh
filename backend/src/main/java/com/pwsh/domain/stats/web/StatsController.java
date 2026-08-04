package com.pwsh.domain.stats.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.domain.stats.service.StatsService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 통계 API — 매핑만, 로직·인가는 {@link StatsService}(관리자 전용).
 * 집계 전용 도메인이라 조회 1건(요약 + 최근 14일 추이).
 */
@RestController
@RequestMapping("/api/adm/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 요약 + 최근 14일 추이 */
    @RequestMapping("/selectStatsList.do")
    public ApiResponse<Map<String, Object>> selectList() {
        return ApiResponse.ok(statsService.selectDashboard());
    }
}
