package com.pwsh.domain.code.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.code.service.CodeService;
import com.pwsh.domain.code.service.CodeVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통코드 관리 (표준 CRUD 패턴 원형).
 * - 컨트롤러는 매핑만, 업무 로직은 {@link CodeService}.
 * - 엔드포인트는 표준 5개(selectList/selectView/insert/update/delete) + {variant} 분기:
 *   · selectCodeList{variant}  : ''=페이징목록 / Tree=계층 / Combo=콤보
 *   · selectCodeView{variant}  : ''=단건 / NextChild=하위코드추가용 다음값
 *   · updateCode{variant}      : ''=수정 / ordr=정렬 교환
 * - 요청 JSON(@RequestBody), 응답 ApiResponse 봉투(에러는 GlobalExceptionHandler), audit은 AuditInterceptor 자동.
 */
@RestController
@RequestMapping("/api/adm/code")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    /** 목록 계열: ''=페이징목록 / Tree=계층 / Combo=콤보 */
    @RequestMapping("/selectCodeList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) CodeVO searchVO) {
        CodeVO vo = searchVO == null ? new CodeVO() : searchVO;
        if ("Tree".equals(variant)) {
            return ApiResponse.ok(codeService.selectTree(vo));
        }
        if ("Combo".equals(variant)) {
            return ApiResponse.ok(codeService.selectComboList(vo));
        }
        int totCnt = codeService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", codeService.selectList(vo),
                "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    /** 단건 계열: ''=단건조회 / NextChild=하위코드추가용 다음값 */
    @RequestMapping("/selectCodeView{variant}.do")
    public ApiResponse<CodeVO> selectView(@PathVariable(name = "variant", required = false) String variant,
                                          @RequestBody CodeVO searchVO) {
        if ("NextChild".equals(variant)) {
            return ApiResponse.ok(codeService.nextChildCode(searchVO));
        }
        return ApiResponse.ok(codeService.selectView(searchVO));
    }

    @RequestMapping("/insertCode.do")
    public ApiResponse<Void> insert(@RequestBody CodeVO searchVO) {
        Validate.required(searchVO.getRowId(), "코드ID");
        Validate.required(searchVO.getPCodeId(), "상위코드");
        Validate.required(searchVO.getCodeNm(), "코드명");
        codeService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. variant: "ordr"=정렬 위/아래 교환(searchCondition=UP/DOWN), 빈값=일반수정 */
    @RequestMapping("/updateCode{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody CodeVO searchVO) {
        if ("Ordr".equals(variant)) {
            codeService.swapOrdr(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getCodeNm(), "코드명");
            codeService.update(searchVO);
        }
        return ApiResponse.ok();
    }

    /** 삭제 (논리) + 같은 부모 내 뒤 순서 당김 */
    @RequestMapping("/deleteCode.do")
    public ApiResponse<Void> delete(@RequestBody CodeVO searchVO) {
        codeService.delete(searchVO);
        return ApiResponse.ok();
    }
}
