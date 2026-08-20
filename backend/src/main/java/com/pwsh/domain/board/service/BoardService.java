package com.pwsh.domain.board.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.global.security.GenAccessGuard;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게시판 정의(설정) 업무 로직. 컨트롤러는 매핑만(단일 @Service).
 * 상세 조회(GEN 스킨 로딩용)는 게시판 단위 접근 인가(GenAccessGuard) 적용.
 */
@Service
@RequiredArgsConstructor
public class BoardService {

    private final CommonDAO commonDAO;
    private final GenAccessGuard genAccessGuard;

    public List<BoardVO> selectList(BoardVO vo) {
        return commonDAO.selectList("boardDAO.selectList", vo);
    }

    public int selectListTotalCount(BoardVO vo) {
        return commonDAO.selectOne("boardDAO.selectListTotalCount", vo);
    }

    /** 콤보(메뉴-게시판 연결용) */
    public List<BoardVO> selectComboList(BoardVO vo) {
        return commonDAO.selectList("boardDAO.selectComboList", vo);
    }

    public BoardVO selectView(BoardVO vo) {
        genAccessGuard.checkBoard(vo.getRowId());
        return commonDAO.selectOne("boardDAO.selectView", vo);
    }

    public void insert(BoardVO vo) {
        commonDAO.insert("boardDAO.insert", vo);
    }

    public void update(BoardVO vo) {
        commonDAO.update("boardDAO.update", vo);
    }

    public void delete(BoardVO vo) {
        commonDAO.delete("boardDAO.delete", vo);
    }
}
