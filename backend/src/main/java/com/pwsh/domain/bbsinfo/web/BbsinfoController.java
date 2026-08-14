package com.pwsh.domain.bbsinfo.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.bbsinfo.service.BbsinfoService;
import com.pwsh.domain.bbsinfo.service.BbsinfoVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 정의(설정) 관리 — 컨트롤러는 매핑만, 로직은 {@link BbsinfoService}.
 * selectBbsinfoList{variant}: ''=목록 / Combo=메뉴 게시판 연결 선택용 콤보
 */
@RestController
@RequestMapping("/api/adm/bbsinfo")
@RequiredArgsConstructor
public class BbsinfoController {

    private final BbsinfoService bbsinfoService;

    /** 목록 계열: ''=페이징목록 / Combo=콤보(권한 예외) */
    @RequestMapping("/selectBbsinfoList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) BbsinfoVO searchVO) {
        BbsinfoVO vo = searchVO == null ? new BbsinfoVO() : searchVO;
        if ("Combo".equals(variant)) {
            return ApiResponse.ok(bbsinfoService.selectComboList(vo));
        }
        int totCnt = bbsinfoService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", bbsinfoService.selectList(vo),
                "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    /** 단건 조회(게시판 설정) — 사용자 게시판 화면에서도 사용(권한 예외) */
    @RequestMapping("/selectBbsinfoView.do")
    public ApiResponse<BbsinfoVO> selectView(@RequestBody BbsinfoVO searchVO) {
        return ApiResponse.ok(bbsinfoService.selectView(searchVO));
    }

    @RequestMapping("/insertBbsinfo.do")
    public ApiResponse<Void> insert(@RequestBody BbsinfoVO searchVO) {
        Validate.required(searchVO.getBbsinfoNm(), "게시판명");
        Validate.required(searchVO.getBbsinfoCd(), "게시판유형");
        bbsinfoService.insert(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/updateBbsinfo.do")
    public ApiResponse<Void> update(@RequestBody BbsinfoVO searchVO) {
        Validate.required(searchVO.getBbsinfoNm(), "게시판명");
        Validate.required(searchVO.getBbsinfoCd(), "게시판유형");
        bbsinfoService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteBbsinfo.do")
    public ApiResponse<Void> delete(@RequestBody BbsinfoVO searchVO) {
        bbsinfoService.delete(searchVO);
        return ApiResponse.ok();
    }
}
