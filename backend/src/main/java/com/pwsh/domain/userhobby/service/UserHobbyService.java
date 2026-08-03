package com.pwsh.domain.userhobby.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원별 취미 레벨(단일 @Service). 항상 로그인 본인 기준(user_id는 서버가 세팅 — 위변조 차단).
 */
@Service
@RequiredArgsConstructor
public class UserHobbyService {

    private final CommonDAO commonDAO;

    /** 본인 취미 레벨 목록 */
    public List<UserHobbyVO> selectMyList() {
        UserHobbyVO vo = new UserHobbyVO();
        vo.setUserId(currentUserId());
        return commonDAO.selectList("userHobbyDAO.selectMyList", vo);
    }

    /** 레벨 설정(있으면 변경, 없으면 등록) */
    @Transactional
    public void saveLevel(UserHobbyVO vo) {
        vo.setUserId(currentUserId());
        Integer cnt = commonDAO.selectOne("userHobbyDAO.selectCountActive", vo);
        if (cnt != null && cnt > 0) {
            commonDAO.update("userHobbyDAO.updateLevel", vo);
        } else {
            commonDAO.insert("userHobbyDAO.insert", vo);
        }
    }

    /** 본인 취미 레벨 삭제 */
    public void deleteMy(UserHobbyVO vo) {
        vo.setUserId(currentUserId());
        commonDAO.delete("userHobbyDAO.deleteMy", vo);
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }
}
