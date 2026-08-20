package com.pwsh.domain.board.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.board.service.BoardService;
import com.pwsh.domain.board.service.BoardVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 정의(설정) 관리 — 컨트롤러는 매핑만, 로직은 {@link BoardService}.
 * selectBoardList{variant}: ''=목록 / Combo=메뉴 게시판 연결 선택용 콤보
 */
@RestController
@RequestMapping("/api/adm/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /** 목록 계열: ''=페이징목록 / Combo=콤보(권한 예외) */
    @RequestMapping("/selectBoardList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) BoardVO searchVO) {
        BoardVO vo = searchVO == null ? new BoardVO() : searchVO;
        if ("Combo".equals(variant)) {
            return ApiResponse.ok(boardService.selectComboList(vo));
        }
        int totalCount = boardService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", boardService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    /** 단건 조회(게시판 설정) — 사용자 게시판 화면에서도 사용(권한 예외) */
    @RequestMapping("/selectBoardView.do")
    public ApiResponse<BoardVO> selectView(@RequestBody BoardVO searchVO) {
        return ApiResponse.ok(boardService.selectView(searchVO));
    }

    @RequestMapping("/insertBoard.do")
    public ApiResponse<Void> insert(@RequestBody BoardVO searchVO) {
        Validate.required(searchVO.getBoardName(), "게시판명");
        Validate.required(searchVO.getTypeCd(), "게시판유형");
        boardService.insert(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/updateBoard.do")
    public ApiResponse<Void> update(@RequestBody BoardVO searchVO) {
        Validate.required(searchVO.getBoardName(), "게시판명");
        Validate.required(searchVO.getTypeCd(), "게시판유형");
        boardService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteBoard.do")
    public ApiResponse<Void> delete(@RequestBody BoardVO searchVO) {
        boardService.delete(searchVO);
        return ApiResponse.ok();
    }
}
