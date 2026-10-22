package com.pwsh.domain.destroylog.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.domain.destroylog.service.DestroyLogService;
import com.pwsh.domain.destroylog.service.DestroyLogVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인정보 파기 이력 (조회 전용 — append-only). 기록은 MemberService가 파기할 때 남긴다.
 * 컨트롤러는 매핑만, 로직은 {@link DestroyLogService}.
 */
@RestController
@RequestMapping("/api/adm/destroylog")
@RequiredArgsConstructor
public class DestroyLogController {

    private final DestroyLogService destroyLogService;

    @RequestMapping("/selectDestroyLogList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) DestroyLogVO searchVO) {
        DestroyLogVO vo = searchVO == null ? new DestroyLogVO() : searchVO;
        int totalCount = destroyLogService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", destroyLogService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }
}
