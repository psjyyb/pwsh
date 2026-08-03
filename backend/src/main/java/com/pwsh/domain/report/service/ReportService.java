package com.pwsh.domain.report.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 신고(단일 @Service). 신고 등록은 로그인 회원(중복신고 차단, reg_id 서버 세팅),
 * 목록/처리는 관리자만(서비스단 admin 검증 — /api/adm/report/**는 PermissionInterceptor 예외라 여기서 인가).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final CommonDAO commonDAO;

    /** 신고 등록 — 로그인 회원, 같은 대상 중복신고 차단. */
    public void insert(ReportVO vo) {
        String me = currentUserId();
        if (!"BBS".equals(vo.getTargetType()) && !"COMMENT".equals(vo.getTargetType())
                && !"RECRUIT".equals(vo.getTargetType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 신고 대상입니다.");
        }
        vo.setRegId(me);
        Integer dup = commonDAO.selectOne("reportDAO.selectDupCnt", vo);
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 신고한 대상입니다.");
        }
        commonDAO.insert("reportDAO.insert", vo);
    }

    /** 신고 목록 — 관리자만. */
    public List<ReportVO> selectList(ReportVO vo) {
        assertAdmin();
        return commonDAO.selectList("reportDAO.selectList", vo);
    }

    /** 신고 처리(RESOLVED/DISMISSED) — 관리자만. */
    public void updateStatus(ReportVO vo) {
        assertAdmin();
        if (!"RESOLVED".equals(vo.getStatus()) && !"DISMISSED".equals(vo.getStatus())
                && !"PENDING".equals(vo.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 상태입니다.");
        }
        commonDAO.update("reportDAO.updateStatus", vo);
    }

    private void assertAdmin() {
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }
}
