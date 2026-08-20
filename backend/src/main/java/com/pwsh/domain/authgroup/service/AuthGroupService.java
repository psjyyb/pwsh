package com.pwsh.domain.authgroup.service;

import com.pwsh.common.CommonDAO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한그룹 업무 로직 + 그룹-메뉴/그룹-사용자 매핑 저장. 컨트롤러는 매핑만(단일 @Service).
 */
@Service
@RequiredArgsConstructor
public class AuthGroupService {

    private final CommonDAO commonDAO;

    public List<AuthGroupVO> selectList(AuthGroupVO vo) {
        return commonDAO.selectList("authGroupDAO.selectList", vo);
    }

    public int selectListTotalCount(AuthGroupVO vo) {
        return commonDAO.selectOne("authGroupDAO.selectListTotalCount", vo);
    }

    public List<AuthGroupVO> selectComboList(AuthGroupVO vo) {
        return commonDAO.selectList("authGroupDAO.selectComboList", vo);
    }

    public AuthGroupVO selectView(AuthGroupVO vo) {
        return commonDAO.selectOne("authGroupDAO.selectView", vo);
    }

    public void insert(AuthGroupVO vo) {
        commonDAO.insert("authGroupDAO.insert", vo);
    }

    public void update(AuthGroupVO vo) {
        commonDAO.update("authGroupDAO.update", vo);
    }

    /** 삭제 — 그룹 행 + 그룹-메뉴(auth)·그룹-사용자(auth_member) 매핑까지 정리(고아 방지) */
    @Transactional
    public void delete(AuthGroupVO vo) {
        vo.setAuthGroupId(vo.getRowId()); // 매핑 삭제 매퍼는 authGroupId(conn_id) 기준
        commonDAO.delete("authGroupDAO.deleteAuthByGrp", vo);
        commonDAO.delete("authGroupDAO.deleteAuthMemberByGroup", vo);
        commonDAO.delete("authGroupDAO.delete", vo);
    }

    /** 그룹의 권한(메뉴) ID 목록 */
    public List<String> selectAuthMenuIds(AuthGroupVO vo) {
        return commonDAO.selectList("authGroupDAO.selectAuthMenuIds", vo);
    }

    /** 그룹-메뉴 권한 저장 — 기존 삭제 후 선택 메뉴 재등록 */
    @Transactional
    public void saveMenu(AuthGroupVO vo) {
        commonDAO.delete("authGroupDAO.deleteAuthByGrp", vo);
        if (vo.getMenuIds() != null) {
            for (String menuId : vo.getMenuIds()) {
                vo.setMenuId(menuId);
                commonDAO.insert("authGroupDAO.insertAuthMenu", vo);
            }
        }
    }

    /** 그룹의 소속 사용자 ID 목록 */
    public List<String> selectMemberIdsByGroup(AuthGroupVO vo) {
        return commonDAO.selectList("authGroupDAO.selectMemberIdsByGroup", vo);
    }

    /** 그룹-사용자 지정 저장 — 기존 삭제 후 재등록 */
    @Transactional
    public void saveMember(AuthGroupVO vo) {
        commonDAO.delete("authGroupDAO.deleteAuthMemberByGroup", vo);
        if (vo.getMemberIds() != null) {
            for (String memberId : vo.getMemberIds()) {
                vo.setMemberId(memberId);
                commonDAO.insert("authGroupDAO.insertAuthMemberByGroup", vo);
            }
        }
    }
}
