package com.pwsh.domain.memberhobby.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원별 취미 레벨(단일 @Service). 항상 로그인 본인 기준(member_id는 서버가 세팅 — 위변조 차단).
 */
@Service
@RequiredArgsConstructor
public class MemberHobbyService {

    private final CommonDAO commonDAO;

    /** 본인 취미 레벨 목록 */
    public List<MemberHobbyVO> selectMyList() {
        MemberHobbyVO vo = new MemberHobbyVO();
        vo.setMemberId(currentMemberId());
        return commonDAO.selectList("memberHobbyDAO.selectMyList", vo);
    }

    /** 레벨 설정(있으면 변경, 없으면 등록) */
    @Transactional
    public void saveLevel(MemberHobbyVO vo) {
        vo.setMemberId(currentMemberId());
        Integer cnt = commonDAO.selectOne("memberHobbyDAO.selectCountActive", vo);
        if (cnt != null && cnt > 0) {
            commonDAO.update("memberHobbyDAO.updateLevel", vo);
        } else {
            commonDAO.insert("memberHobbyDAO.insert", vo);
        }
    }

    /** 본인 취미 레벨 삭제 */
    public void deleteMy(MemberHobbyVO vo) {
        vo.setMemberId(currentMemberId());
        commonDAO.delete("memberHobbyDAO.deleteMy", vo);
    }

    private String currentMemberId() {
        String me = SecurityUtil.getCurrentMemberId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }
}
