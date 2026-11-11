package com.pwsh.domain.batch.web;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.batch.service.BatchJobVO;
import com.pwsh.domain.batch.service.BatchLogVO;
import com.pwsh.domain.batch.service.BatchService;
import com.pwsh.global.security.SecurityUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 작업 관리 — 컨트롤러는 매핑만, 로직은 {@link BatchService}.
 * selectBatchList{variant}: ''=작업 목록 / Log=실행 이력(작업·결과 필터, 페이징)
 * updateBatch{variant}: ''=주기·자동 실행 변경 / Run=즉시 실행
 * (작업은 코드와 1:1이라 등록·삭제가 없다)
 *
 * <p>⚠ 즉시 실행은 휴면 전환·개인정보 파기·파일 삭제를 <b>실제로</b> 일으킨다. 메뉴권한만 믿지 않고 관리자 여부를
 * 직접 확인한다 — 운영자가 실수로 이 메뉴를 다른 그룹에 열어 줘도 여기서 막힌다(시스템 상태와 같은 방식).
 */
@RestController
@RequestMapping("/api/adm/batch")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    private void requireAdmin() {
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 작업 목록은 몇 개뿐이라 페이징하지 않는다. 이력(Log)은 이력 VO의 작업·결과 필터와 페이징을 쓴다 */
    @RequestMapping("/selectBatchList{variant}.do")
    public ApiResponse<Map<String, Object>> selectList(@PathVariable(name = "variant", required = false) String variant,
                                                       @RequestBody(required = false) BatchLogVO searchVO) {
        requireAdmin();
        if ("Log".equals(variant)) {
            BatchLogVO vo = searchVO == null ? new BatchLogVO() : searchVO;
            int totalCount = batchService.selectLogListTotalCount(vo);
            return ApiResponse.ok(Map.of(
                    "list", batchService.selectLogList(vo),
                    "totalCount", totalCount,
                    "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
        }
        BatchJobVO vo = new BatchJobVO();
        int totalCount = batchService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", batchService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(1, Math.max(totalCount, 1), totalCount)));
    }

    @RequestMapping("/selectBatchView.do")
    public ApiResponse<BatchJobVO> selectView(@RequestBody BatchJobVO searchVO) {
        requireAdmin();
        Validate.required(searchVO.getRowId(), "작업");
        Validate.numeric(searchVO.getRowId(), "작업");
        return ApiResponse.ok(batchService.selectView(searchVO));
    }

    /** ''=주기·자동 실행 변경 / Run=즉시 실행(→ 실행 이력 ID, 작업은 뒤에서 돈다) */
    @RequestMapping("/updateBatch{variant}.do")
    public ApiResponse<?> update(@PathVariable(name = "variant", required = false) String variant,
                                 @RequestBody BatchJobVO searchVO) {
        requireAdmin();
        Validate.required(searchVO.getRowId(), "작업");
        Validate.numeric(searchVO.getRowId(), "작업");
        if ("Run".equals(variant)) {
            return ApiResponse.ok(batchService.runNow(searchVO));
        }
        if (StringUtil.isEmpty(variant)) {
            batchService.update(searchVO);
        }
        return ApiResponse.ok();
    }
}
