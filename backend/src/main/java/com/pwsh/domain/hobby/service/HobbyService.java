package com.pwsh.domain.hobby.service;

import com.pwsh.common.CommonDAO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 취미 카탈로그 업무 로직(단일 @Service). 목록/조회는 공개(SecurityConfig permitAll),
 * 등록/수정/삭제는 관리자(PermissionInterceptor: /adm/hobby 메뉴 권한).
 */
@Service
@RequiredArgsConstructor
public class HobbyService {

    private final CommonDAO commonDAO;

    public List<HobbyVO> selectList(HobbyVO vo) {
        return commonDAO.selectList("hobbyDAO.selectList", vo);
    }

    public int selectListTotCnt(HobbyVO vo) {
        return commonDAO.selectOne("hobbyDAO.selectListTotCnt", vo);
    }

    public HobbyVO selectView(HobbyVO vo) {
        return commonDAO.selectOne("hobbyDAO.selectView", vo);
    }

    public void insert(HobbyVO vo) {
        commonDAO.insert("hobbyDAO.insert", vo);
    }

    public void update(HobbyVO vo) {
        commonDAO.update("hobbyDAO.update", vo);
    }

    public void delete(HobbyVO vo) {
        commonDAO.delete("hobbyDAO.delete", vo);
    }
}
