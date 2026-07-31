package com.pwsh.domain.bbsinfo.service;

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
public class BbsinfoService {

    private final CommonDAO commonDAO;
    private final GenAccessGuard genAccessGuard;

    public List<BbsinfoVO> selectList(BbsinfoVO vo) {
        return commonDAO.selectList("bbsinfoDAO.selectList", vo);
    }

    public int selectListTotCnt(BbsinfoVO vo) {
        return commonDAO.selectOne("bbsinfoDAO.selectListTotCnt", vo);
    }

    /** 콤보(메뉴-게시판 연결용) */
    public List<BbsinfoVO> selectComboList(BbsinfoVO vo) {
        return commonDAO.selectList("bbsinfoDAO.selectComboList", vo);
    }

    public BbsinfoVO selectView(BbsinfoVO vo) {
        genAccessGuard.checkBoard(vo.getDbKey());
        return commonDAO.selectOne("bbsinfoDAO.selectView", vo);
    }

    public void insert(BbsinfoVO vo) {
        commonDAO.insert("bbsinfoDAO.insert", vo);
    }

    public void update(BbsinfoVO vo) {
        commonDAO.update("bbsinfoDAO.update", vo);
    }

    public void delete(BbsinfoVO vo) {
        commonDAO.delete("bbsinfoDAO.delete", vo);
    }
}
