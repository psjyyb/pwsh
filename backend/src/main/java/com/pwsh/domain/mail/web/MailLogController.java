package com.pwsh.domain.mail.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.mail.service.MailLogVO;
import com.pwsh.domain.mail.service.MailService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메일 발송 이력 — 컨트롤러는 매핑만, 로직은 {@link MailService}(메일 도메인 단일 서비스).
 *
 * <p>이력은 append-only라 수정·삭제 API가 없다(보존기간 정리는 스케줄러가 한다).
 * {@code insertMailLog}는 "이력을 만든다"=<b>실제로 한 통 보낸다</b>는 뜻이다 — 템플릿 확인용 발송.
 */
@RestController
@RequestMapping("/api/adm/maillog")
@RequiredArgsConstructor
public class MailLogController {

    private final MailService mailService;

    @RequestMapping("/selectMailLogList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) MailLogVO searchVO) {
        MailLogVO vo = searchVO == null ? new MailLogVO() : searchVO;
        int totalCount = mailService.selectLogListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", mailService.selectLogList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectMailLogView.do")
    public ApiResponse<MailLogVO> selectView(@RequestBody MailLogVO searchVO) {
        return ApiResponse.ok(mailService.selectLogView(searchVO));
    }

    /** 템플릿으로 1통 발송(관리자 확인용). 결과는 이 목록에 그대로 남는다. */
    @RequestMapping("/insertMailLog.do")
    public ApiResponse<Boolean> insert(@RequestBody MailLogVO searchVO) {
        Validate.required(searchVO.getTemplateCd(), "템플릿 코드");
        Validate.required(searchVO.getToEmail(), "수신자");
        return ApiResponse.ok(mailService.send(searchVO.getTemplateCd(), searchVO.getToEmail(), searchVO.getVars()));
    }
}
