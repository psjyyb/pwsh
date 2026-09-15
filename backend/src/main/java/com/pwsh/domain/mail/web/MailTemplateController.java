package com.pwsh.domain.mail.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.mail.service.MailService;
import com.pwsh.domain.mail.service.MailTemplateVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메일 템플릿 관리 — 컨트롤러는 매핑만, 로직은 {@link MailService}(메일 도메인 단일 서비스).
 * selectMailTemplateList{variant}: ''=목록 / Preview=치환 미리보기(발송 없음)
 */
@RestController
@RequestMapping("/api/adm/mailtemplate")
@RequiredArgsConstructor
public class MailTemplateController {

    private final MailService mailService;

    /** 목록 계열: ''=페이징목록 / Preview=저장 전 치환 결과 확인(발송하지 않는다) */
    @RequestMapping("/selectMailTemplateList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) MailTemplateVO searchVO) {
        MailTemplateVO vo = searchVO == null ? new MailTemplateVO() : searchVO;
        if ("Preview".equals(variant)) {
            Validate.required(vo.getTemplateCd(), "템플릿 코드");
            return ApiResponse.ok(mailService.preview(vo.getTemplateCd(), vo.getVars()));
        }
        int totalCount = mailService.selectTemplateListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", mailService.selectTemplateList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectMailTemplateView.do")
    public ApiResponse<MailTemplateVO> selectView(@RequestBody MailTemplateVO searchVO) {
        return ApiResponse.ok(mailService.selectTemplateView(searchVO));
    }

    @RequestMapping("/insertMailTemplate.do")
    public ApiResponse<Void> insert(@RequestBody MailTemplateVO searchVO) {
        validate(searchVO);
        mailService.insertTemplate(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/updateMailTemplate{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody MailTemplateVO searchVO) {
        if (StringUtil.isEmpty(variant)) {
            validate(searchVO);
            mailService.updateTemplate(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteMailTemplate.do")
    public ApiResponse<Void> delete(@RequestBody MailTemplateVO searchVO) {
        mailService.deleteTemplate(searchVO);
        return ApiResponse.ok();
    }

    private void validate(MailTemplateVO vo) {
        Validate.required(vo.getTemplateCd(), "템플릿 코드");
        Validate.required(vo.getName(), "템플릿 이름");
        Validate.required(vo.getSubject(), "메일 제목");
        Validate.required(vo.getContent(), "본문");
    }
}
