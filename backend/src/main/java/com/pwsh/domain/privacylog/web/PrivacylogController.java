package com.pwsh.domain.privacylog.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.domain.privacylog.service.PrivacyLogService;
import com.pwsh.domain.privacylog.service.PrivacylogVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인정보 접근 로그 (조회 전용 — append-only). 컨트롤러는 매핑만, 로직은 {@link PrivacyLogService}.
 *
 * <p>등록·수정·삭제 API는 두지 않는다. 적재는 인터셉터가 자동으로 하고,
 * 고쳐 쓸 수 있는 접근기록은 기록으로서 쓸모가 없다.
 */
@RestController
@RequestMapping("/api/adm/privacylog")
@RequiredArgsConstructor
public class PrivacylogController {

    private final PrivacyLogService privacyLogService;

    @RequestMapping("/selectPrivacylogList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) PrivacylogVO searchVO) {
        PrivacylogVO vo = searchVO == null ? new PrivacylogVO() : searchVO;
        int totalCount = privacyLogService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", privacyLogService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectPrivacylogView.do")
    public ApiResponse<PrivacylogVO> selectView(@RequestBody PrivacylogVO searchVO) {
        return ApiResponse.ok(privacyLogService.selectView(searchVO));
    }
}
