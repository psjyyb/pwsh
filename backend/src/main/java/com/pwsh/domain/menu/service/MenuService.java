package com.pwsh.domain.menu.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 업무 로직. 컨트롤러는 매핑만, 로직·트랜잭션은 여기(단일 @Service).
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final CommonDAO commonDAO;

    public List<MenuVO> selectList(MenuVO vo) {
        return commonDAO.selectList("menuDAO.selectList", vo);
    }

    public int selectListTotalCount(MenuVO vo) {
        return commonDAO.selectOne("menuDAO.selectListTotalCount", vo);
    }

    public MenuVO selectView(MenuVO vo) {
        return commonDAO.selectOne("menuDAO.selectView", vo);
    }

    /** 관리 화면용 계층 트리(권한필터 없이 area 전체) */
    public List<MenuVO> selectManageTree(MenuVO vo) {
        return commonDAO.selectList("menuDAO.selectManageTree", vo);
    }

    /** 사이드바 트리 — 로그인 사용자 권한으로 필터('admin'은 전체, 미로그인은 GUEST 그룹). */
    public List<MenuVO> selectMenuTree(MenuVO vo) {
        String memberId = SecurityUtil.getCurrentMemberId();
        // SecurityUtil은 미로그인 시 'system'을 반환 → 이 경우 memberId=null로 넘겨 매퍼가 GUEST 그룹 권한으로 필터.
        boolean anonymous = memberId == null || "system".equals(memberId);
        vo.setMemberId(anonymous ? null : memberId);
        vo.setSuperYn("admin".equals(memberId) ? "Y" : "N");
        return commonDAO.selectList("menuDAO.selectMenuTree", vo);
    }

    public void insert(MenuVO vo) {
        commonDAO.insert("menuDAO.insert", vo);
    }

    public void update(MenuVO vo) {
        commonDAO.update("menuDAO.update", vo);
    }

    /** 같은 부모 내 인접 메뉴와 sortNo 교환. uq(p_menu_id, sortNo) 회피 위해 임시값(-1) 3단계, 트랜잭션. */
    @Transactional
    public void swapSort(MenuVO vo) {
        MenuVO cur = commonDAO.selectOne("menuDAO.selectView", vo);
        if (cur == null) {
            return;
        }
        cur.setDirection(vo.getDirection());
        MenuVO adj = commonDAO.selectOne("menuDAO.selectAdjacentSort", cur);
        if (adj == null) {
            return;
        }
        setSortNo(cur.getRowId(), "-1");
        setSortNo(adj.getRowId(), cur.getSortNo());
        setSortNo(cur.getRowId(), adj.getSortNo());
    }

    private void setSortNo(String rowId, String sortNo) {
        MenuVO v = new MenuVO();
        v.setRowId(rowId);
        v.setSortNo(sortNo);
        commonDAO.update("menuDAO.updatesort", v);
    }

    /** 삭제(논리) + 같은 영역·부모 내 뒤 순서 당김 */
    @Transactional
    public void delete(MenuVO vo) {
        commonDAO.delete("menuDAO.delete", vo);
        commonDAO.update("menuDAO.shiftSortAfterDelete", vo);
    }
}
