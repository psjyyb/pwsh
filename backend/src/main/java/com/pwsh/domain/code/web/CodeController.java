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
 * - 엔드포인트는 표준 5개(selectList/selectView/insert/update/delete) + {path} 분기:
 *   · selectCodeList{path}  : ''=페이징목록 / Tree=계층 / Combo=콤보
 *   · selectCodeView{path}  : ''=단건 / NextChild=하위코드추가용 다음값
 *   · updateCode{path}      : ''=수정 / ordr=정렬 교환
 * - 요청 JSON(@RequestBody), 응답 ApiResponse 봉투(에러는 GlobalExceptionHandler), audit은 AuditInterceptor 자동.
 */
@RestController
@RequestMapping("/api/adm/code")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    /** 목록 계열: ''=페이징목록 / Tree=계층 / Combo=콤보 */
    @RequestMapping("/selectCodeList{path}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "path", required = false) String path,
                                     @RequestBody(required = false) CodeVO searchVO) {
        CodeVO vo = searchVO == null ? new CodeVO() : searchVO;
        if ("Tree".equals(path)) {
            return ApiResponse.ok(codeService.selectTree(vo));
        }
        if ("Combo".equals(path)) {
            return ApiResponse.ok(codeService.selectComboList(vo));
        }
        int totCnt = codeService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", codeService.selectList(vo),
                "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    /** 단건 계열: ''=단건조회 / NextChild=하위코드추가용 다음값 */
    @RequestMapping("/selectCodeView{path}.do")
    public ApiResponse<CodeVO> selectView(@PathVariable(name = "path", required = false) String path,
                                          @RequestBody CodeVO searchVO) {
        if ("NextChild".equals(path)) {
            return ApiResponse.ok(codeService.nextChildCode(searchVO));
        }
        return ApiResponse.ok(codeService.selectView(searchVO));
    }

    @RequestMapping("/insertCode.do")
    public ApiResponse<Void> insert(@RequestBody CodeVO searchVO) {
        Validate.required(searchVO.getDbKey(), "코드ID");
        Validate.required(searchVO.getPCodeId(), "상위코드");
        Validate.required(searchVO.getCodeNm(), "코드명");
        codeService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. path: "ordr"=정렬 위/아래 교환(searchCondition=UP/DOWN), 빈값=일반수정 */
    @RequestMapping("/updateCode{path}.do")
    public ApiResponse<Void> update(@PathVariable(name = "path", required = false) String path,
                                    @RequestBody CodeVO searchVO) {
        if ("Ordr".equals(path)) {
            codeService.swapOrdr(searchVO);
        } else if (StringUtil.isEmpty(path)) {
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
