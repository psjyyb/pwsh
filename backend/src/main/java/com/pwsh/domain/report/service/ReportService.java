package com.pwsh.domain.report.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // 숫자 검증(::integer 캐스트 500 방지) + 대상 존재 확인(없는 콘텐츠 신고 차단)
        if (vo.getTargetId() == null || !vo.getTargetId().matches("\\d+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 신고 대상입니다.");
        }
        Integer targetExists = commonDAO.selectOne("reportDAO.countTarget", vo);
        if (targetExists == null || targetExists == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "신고 대상을 찾을 수 없습니다.");
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

    /** 신고 총건수(페이징) — 목록과 동일한 상태 필터. */
    public int selectListTotCnt(ReportVO vo) {
        assertAdmin();
        return commonDAO.selectOne("reportDAO.selectListTotCnt", vo);
    }

    /**
     * 신고 처리 — 관리자만.
     * RESOLVED(삭제조치): 대상 콘텐츠를 숨김(use_yn='N'). PENDING(되돌리기): 대상 복원(use_yn='Y').
     * DISMISSED(반려): 콘텐츠는 건드리지 않음(오신고 처리).
     */
    @Transactional
    public void updateStatus(ReportVO vo) {
        assertAdmin();
        String status = vo.getStatus();
        if (!"RESOLVED".equals(status) && !"DISMISSED".equals(status) && !"PENDING".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 상태입니다.");
        }
        // 삭제조치/되돌리기는 대상 콘텐츠의 노출 여부까지 변경(대상 유형은 서버에서 재조회 — 위변조 차단)
        if ("RESOLVED".equals(status) || "PENDING".equals(status)) {
            ReportVO report = commonDAO.selectOne("reportDAO.selectView", vo);
            if (report != null) {
                ReportVO t = new ReportVO();
                t.setTargetId(report.getTargetId());
                t.setUseYn("RESOLVED".equals(status) ? "N" : "Y");
                String type = report.getTargetType();
                if ("BBS".equals(type)) {
                    commonDAO.update("reportDAO.setBbsUseYn", t);
                } else if ("COMMENT".equals(type)) {
                    commonDAO.update("reportDAO.setCommentUseYn", t);
                } else if ("RECRUIT".equals(type)) {
                    commonDAO.update("reportDAO.setRecruitUseYn", t);
                }
            }
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
