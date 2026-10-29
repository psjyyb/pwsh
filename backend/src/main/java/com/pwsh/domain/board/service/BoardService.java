package com.pwsh.domain.board.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.GenAccessGuard;
import com.pwsh.global.security.SecurityUtil;
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

    /**
     * 콤보(메뉴-게시판 연결·취미 게시판 지정용) — 관리자 전용.
     * 비공개 게시판까지 ID·이름이 다 나오므로 열어 두면 누구나 게시판 구성을 훑을 수 있다(v0.6.26까지는 비로그인에게도 열려 있었다).
     * 메뉴관리·취미 관리 권한만 받은 관리자도 써야 해서 메뉴권한 인터셉터는 면제하고 여기서 관리자 여부를 본다.
     */
    public List<BoardVO> selectComboList(BoardVO vo) {
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
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
