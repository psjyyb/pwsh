package com.pwsh.domain.eventlog.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.domain.eventlog.service.EventLogService;
import com.pwsh.domain.eventlog.service.EventlogVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이벤트 로그 관리 (조회 전용 — append-only). 컨트롤러는 매핑만, 로직은 {@link EventLogService}.
 * (EventLogService는 감사 기록 write()도 담당 — eventlog 도메인 단일 서비스로 공유.)
 */
@RestController
@RequestMapping("/api/adm/eventlog")
@RequiredArgsConstructor
public class EventlogController {

    private final EventLogService eventLogService;

    @RequestMapping("/selectEventlogList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) EventlogVO searchVO) {
        EventlogVO vo = searchVO == null ? new EventlogVO() : searchVO;
        int totCnt = eventLogService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", eventLogService.selectList(vo),
                "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    @RequestMapping("/selectEventlogView.do")
    public ApiResponse<EventlogVO> selectView(@RequestBody EventlogVO searchVO) {
        return ApiResponse.ok(eventLogService.selectView(searchVO));
    }
}
