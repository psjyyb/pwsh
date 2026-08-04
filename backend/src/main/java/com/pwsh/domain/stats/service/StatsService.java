package com.pwsh.domain.stats.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 관리자 대시보드 통계(단일 @Service, 집계 전용 — 저장 테이블 없음).
 * /api/adm/stats/**는 대응 메뉴가 없어 PermissionInterceptor 예외 경로이므로 여기서 관리자 인가를 강제한다.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    /** 추이 조회 일수(2주) */
    private static final int TREND_DAYS = 14;

    private final CommonDAO commonDAO;

    /** 요약(누적/현재) + 최근 14일 일자별 추이(가입·게시글·모집). */
    public Map<String, Object> selectDashboard() {
        assertAdmin();
        Map<String, Object> param = new HashMap<>();
        param.put("days", String.valueOf(TREND_DAYS));
        List<Map<String, Object>> daily = commonDAO.selectList("statsDAO.selectDailyTrend", param);
        Map<String, Object> totals = commonDAO.selectOne("statsDAO.selectTotals", null);

        Map<String, Object> result = new HashMap<>();
        result.put("totals", totals);
        result.put("daily", daily);
        result.put("days", TREND_DAYS);
        return result;
    }

    private void assertAdmin() {
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
