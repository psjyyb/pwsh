package com.pwsh.domain.report.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.report.service.ReportService;
import com.pwsh.domain.report.service.ReportVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 — 등록(로그인 회원), 목록/처리(관리자, 서비스단 인가). 로직은 {@link ReportService}.
 */
@RestController
@RequestMapping("/api/adm/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 신고 등록. body {targetType, targetId, reason} */
    @RequestMapping("/insertReport.do")
    public ApiResponse<Void> insert(@RequestBody ReportVO vo) {
        Validate.required(vo.getTargetType(), "신고 대상 유형");
        Validate.required(vo.getTargetId(), "신고 대상");
        Validate.required(vo.getReason(), "신고 사유");
        reportService.insert(vo);
        return ApiResponse.ok();
    }

    /** 신고 목록(관리자, 페이징). body {status?, pageIndex, size} */
    @RequestMapping("/selectReportList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody ReportVO vo) {
        int totCnt = reportService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", reportService.selectList(vo),
                "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    /** 신고 처리(관리자). body {rowId, status} */
    @RequestMapping("/updateReportStatus.do")
    public ApiResponse<Void> updateStatus(@RequestBody ReportVO vo) {
        Validate.required(vo.getRowId(), "신고");
        Validate.required(vo.getStatus(), "상태");
        reportService.updateStatus(vo);
        return ApiResponse.ok();
    }
}
