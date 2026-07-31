package com.pwsh.domain.bbs.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.bbs.service.BbsService;
import com.pwsh.domain.bbs.service.BbsVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 (사용자/관리자 공용, 권한 예외 — 로그인만 필요). 컨트롤러는 매핑만, 로직은 {@link BbsService}.
 */
@RestController
@RequestMapping("/api/adm/bbs")
@RequiredArgsConstructor
public class BbsController {

    private final BbsService bbsService;

    @RequestMapping("/selectBbsList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody BbsVO searchVO) {
        int totCnt = bbsService.selectListTotCnt(searchVO);
        return ApiResponse.ok(Map.of(
                "list", bbsService.selectList(searchVO),
                "totCnt", totCnt,
                "page", PageUtil.of(searchVO.getPageIndex(), searchVO.getSize(), totCnt)));
    }

    @RequestMapping("/selectBbsView.do")
    public ApiResponse<BbsVO> selectView(@RequestBody BbsVO searchVO) {
        return ApiResponse.ok(bbsService.selectView(searchVO));
    }

    /** 등록 후 생성된 게시글 ID 반환(첨부 매핑 연결용) */
    @RequestMapping("/insertBbs.do")
    public ApiResponse<String> insert(@RequestBody BbsVO searchVO) {
        Validate.required(searchVO.getBbsinfoId(), "게시판");
        Validate.required(searchVO.getTitle(), "제목");
        bbsService.insert(searchVO);
        return ApiResponse.ok(searchVO.getDbKey());
    }

    @RequestMapping("/updateBbs.do")
    public ApiResponse<Void> update(@RequestBody BbsVO searchVO) {
        Validate.required(searchVO.getTitle(), "제목");
        bbsService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteBbs.do")
    public ApiResponse<Void> delete(@RequestBody BbsVO searchVO) {
        bbsService.delete(searchVO);
        return ApiResponse.ok();
    }
}
