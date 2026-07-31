package com.pwsh.domain.authgrp.service;

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
public class AuthgrpService {

    private final CommonDAO commonDAO;

    public List<AuthgrpVO> selectList(AuthgrpVO vo) {
        return commonDAO.selectList("authgrpDAO.selectList", vo);
    }

    public int selectListTotCnt(AuthgrpVO vo) {
        return commonDAO.selectOne("authgrpDAO.selectListTotCnt", vo);
    }

    public List<AuthgrpVO> selectComboList(AuthgrpVO vo) {
        return commonDAO.selectList("authgrpDAO.selectComboList", vo);
    }

    public AuthgrpVO selectView(AuthgrpVO vo) {
        return commonDAO.selectOne("authgrpDAO.selectView", vo);
    }

    public void insert(AuthgrpVO vo) {
        commonDAO.insert("authgrpDAO.insert", vo);
    }

    public void update(AuthgrpVO vo) {
        commonDAO.update("authgrpDAO.update", vo);
    }

    /** 삭제 — 그룹 행 + 그룹-메뉴(t_auth)·그룹-사용자(t_auth_user) 매핑까지 정리(고아 방지) */
    @Transactional
    public void delete(AuthgrpVO vo) {
        vo.setAuthgrpId(vo.getDbKey()); // 매핑 삭제 매퍼는 authgrpId(conn_id) 기준
        commonDAO.delete("authgrpDAO.deleteAuthByGrp", vo);
        commonDAO.delete("authgrpDAO.deleteAuthUserByGrp", vo);
        commonDAO.delete("authgrpDAO.delete", vo);
    }

    /** 그룹의 권한(메뉴) ID 목록 */
    public List<String> selectAuthMenuIds(AuthgrpVO vo) {
        return commonDAO.selectList("authgrpDAO.selectAuthMenuIds", vo);
    }

    /** 그룹-메뉴 권한 저장 — 기존 삭제 후 선택 메뉴 재등록 */
    @Transactional
    public void saveMenu(AuthgrpVO vo) {
        commonDAO.delete("authgrpDAO.deleteAuthByGrp", vo);
        if (vo.getMenuIds() != null) {
            for (String menuId : vo.getMenuIds()) {
                vo.setMenuId(menuId);
                commonDAO.insert("authgrpDAO.insertAuthMenu", vo);
            }
        }
    }

    /** 그룹의 소속 사용자 ID 목록 */
    public List<String> selectUserIdsByGrp(AuthgrpVO vo) {
        return commonDAO.selectList("authgrpDAO.selectUserIdsByGrp", vo);
    }

    /** 그룹-사용자 지정 저장 — 기존 삭제 후 재등록 */
    @Transactional
    public void saveUser(AuthgrpVO vo) {
        commonDAO.delete("authgrpDAO.deleteAuthUserByGrp", vo);
        if (vo.getUserIds() != null) {
            for (String userId : vo.getUserIds()) {
                vo.setUserId(userId);
                commonDAO.insert("authgrpDAO.insertAuthUserByGrp", vo);
            }
        }
    }
}
