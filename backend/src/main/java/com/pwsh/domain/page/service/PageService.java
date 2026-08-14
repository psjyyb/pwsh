package com.pwsh.domain.page.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.global.security.GenAccessGuard;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 페이지(단일 콘텐츠) 업무 로직. 컨트롤러는 매핑만(단일 @Service).
 * 상세 조회 진입부에서 페이지 단위 접근 인가(GenAccessGuard) — 비회원/회원 딥링크 차단.
 */
@Service
@RequiredArgsConstructor
public class PageService {

    private final CommonDAO commonDAO;
    private final GenAccessGuard genAccessGuard;

    public List<PageVO> selectList(PageVO vo) {
        return commonDAO.selectList("pageDAO.selectList", vo);
    }

    public int selectListTotCnt(PageVO vo) {
        return commonDAO.selectOne("pageDAO.selectListTotCnt", vo);
    }

    public PageVO selectView(PageVO vo) {
        genAccessGuard.checkPage(vo.getRowId());
        return commonDAO.selectOne("pageDAO.selectView", vo);
    }

    public void insert(PageVO vo) {
        commonDAO.insert("pageDAO.insert", vo);
    }

    public void update(PageVO vo) {
        commonDAO.update("pageDAO.update", vo);
    }

    public void delete(PageVO vo) {
        commonDAO.delete("pageDAO.delete", vo);
    }
}
