package com.pwsh.domain.user.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.eventlog.service.EventLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 업무 로직. 컨트롤러는 매핑·입력검증만, 로직(ID중복·BCrypt·매핑 저장)은 여기(단일 @Service).
 * 비밀번호 복잡도 검증(PasswordPolicy)은 컨트롤러 진입부(인코딩 전 원문 검사).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final CommonDAO commonDAO;
    private final PasswordEncoder passwordEncoder;
    private final EventLogService eventLogService;

    public List<UserVO> selectList(UserVO vo) {
        return commonDAO.selectList("userDAO.selectList", vo);
    }

    public int selectListTotCnt(UserVO vo) {
        return commonDAO.selectOne("userDAO.selectListTotCnt", vo);
    }

    public UserVO selectView(UserVO vo) {
        return commonDAO.selectOne("userDAO.selectView", vo);
    }

    /** 등록: ID 중복검사 + 비번 BCrypt 인코딩 */
    public void insert(UserVO vo) {
        Integer cnt = commonDAO.selectOne("userDAO.selectCount", vo);
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 존재하는 사용자 ID입니다.");
        }
        vo.setUserPw(passwordEncoder.encode(vo.getUserPw()));
        commonDAO.insert("userDAO.insert", vo);
    }

    /** 비밀번호 변경(BCrypt) — 관리자 리셋. 대상 사용자의 token_ver를 올려 기존 세션 무효화. */
    @Transactional
    public void updatePassword(UserVO vo) {
        vo.setUserPw(passwordEncoder.encode(vo.getUserPw()));
        commonDAO.update("userDAO.updatePw", vo);
        UserVO t = new UserVO();
        t.setUserId(vo.getDbKey()); // updatePw는 dbKey(=user_id) 기준
        commonDAO.selectOne("userDAO.incrementTokenVer", t);
    }

    /** 정보 수정(비번 제외) */
    public void updateInfo(UserVO vo) {
        commonDAO.update("userDAO.updateInfo", vo);
    }

    /** 삭제(논리, 탈퇴) — 사용자의 권한그룹 매핑(t_auth_user)도 정리(고아 방지) */
    @Transactional
    public void delete(UserVO vo) {
        vo.setUserId(vo.getDbKey()); // 매핑 삭제는 userId 기준
        commonDAO.delete("userDAO.deleteAuthUser", vo);
        commonDAO.delete("userDAO.delete", vo);
    }

    /** 사용자의 권한그룹 ID 목록 */
    public List<String> selectAuthgrpIds(UserVO vo) {
        return commonDAO.selectList("userDAO.selectAuthgrpIds", vo);
    }

    /** 관리자 강제 로그아웃 — 대상 사용자의 token_ver +1로 발급된 토큰(access·refresh) 즉시 무효화. */
    public void forceLogout(UserVO vo) {
        commonDAO.selectOne("userDAO.incrementTokenVer", vo);
        eventLogService.write("USER_LOGOUT", "t_user", vo.getUserId());
    }

    /**
     * 관리자 제재(계정 상태 변경) — STATUS03(정지) / STATUS01(정상 해제).
     * 정지 시 token_ver를 올려 이미 발급된 access 토큰까지 즉시 무효화한다
     * (JWT 필터는 상태가 아니라 token_ver로 판정하므로, 올리지 않으면 만료 전까지 계속 접근 가능).
     */
    @Transactional
    public void updateStatus(UserVO vo) {
        String status = vo.getStatusCd();
        if (!"STATUS01".equals(status) && !"STATUS03".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정상(STATUS01) 또는 정지(STATUS03)만 지정할 수 있습니다.");
        }
        commonDAO.update("userDAO.updateStatus", vo);
        if ("STATUS03".equals(status)) {
            commonDAO.selectOne("userDAO.incrementTokenVer", vo); // 정지 즉시 접근 차단
        }
        // 감사: 컨트롤러 진입점이 updateStatus라 AOP 대상이 아니므로 여기서 직접 남긴다(제재는 추적 필수).
        eventLogService.write("STATUS03".equals(status) ? "USER_SUSPEND" : "USER_RESTORE", "t_user", vo.getUserId());
    }

    /** 사용자-권한그룹 매핑 저장 — 기존 삭제 후 재등록 */
    @Transactional
    public void saveAuthgrp(UserVO vo) {
        commonDAO.delete("userDAO.deleteAuthUser", vo);
        if (vo.getAuthgrpIds() != null) {
            for (String authgrpId : vo.getAuthgrpIds()) {
                vo.setAuthgrpId(authgrpId);
                commonDAO.insert("userDAO.insertAuthUser", vo);
            }
        }
        eventLogService.write("USER_AUTHGRP", "t_user", vo.getUserId());
    }
}
