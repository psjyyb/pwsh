package com.pwsh.domain.recruit.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집(취미 모임원 모집) + 참여신청 업무 로직. 컨트롤러는 매핑만(단일 @Service).
 * - 조회(목록/상세)는 공개(SecurityConfig permitAll), 쓰기/신청/수락은 로그인 필요.
 * - 모집 수정/삭제·마감·신청 수락/거절 = 주최자 또는 관리자(assertOwnerOrAdmin).
 * - 신청 취소 = 신청자 본인 또는 관리자.
 */
@Service
@RequiredArgsConstructor
public class RecruitService {

    private final CommonDAO commonDAO;

    // ===== 모집 =====
    public List<RecruitVO> selectList(RecruitVO vo) {
        return commonDAO.selectList("recruitDAO.selectList", vo);
    }

    public int selectListTotCnt(RecruitVO vo) {
        return commonDAO.selectOne("recruitDAO.selectListTotCnt", vo);
    }

    /** 상세 + 조회수 증가. */
    public RecruitVO selectView(RecruitVO vo) {
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", vo);
        if (recruit == null) {
            return null;
        }
        commonDAO.update("recruitDAO.updateViewCnt", vo);
        return recruit;
    }

    /** 등록(로그인 회원). 주최자=reg_id(AuditInterceptor), 상태=모집중 기본. */
    public void insert(RecruitVO vo) {
        commonDAO.insert("recruitDAO.insert", vo);
    }

    /** 수정 — 주최자·관리자만. */
    public void update(RecruitVO vo) {
        assertOwner(vo.getDbKey());
        commonDAO.update("recruitDAO.update", vo);
    }

    /** 모집 상태 변경(마감/재개) — 주최자·관리자만. */
    public void updateStatus(RecruitVO vo) {
        assertOwner(vo.getDbKey());
        commonDAO.update("recruitDAO.updateStatus", vo);
    }

    /** 삭제(논리) + 딸린 신청 일괄 비활성 — 주최자·관리자만. */
    @Transactional
    public void delete(RecruitVO vo) {
        assertOwner(vo.getDbKey());
        commonDAO.delete("recruitDAO.delete", vo);
        RecruitApplyVO applyParam = new RecruitApplyVO();
        applyParam.setRecruitId(vo.getDbKey());
        commonDAO.update("recruitDAO.deleteApplyByRecruit", applyParam);
    }

    // ===== 참여 신청 =====
    /** 특정 모집의 신청자 목록 — 주최자·관리자만. */
    public List<RecruitApplyVO> selectApplyList(RecruitApplyVO vo) {
        assertOwner(vo.getRecruitId());
        return commonDAO.selectList("recruitDAO.selectApplyList", vo);
    }

    /** 내 신청 내역(로그인 본인). */
    public List<RecruitApplyVO> selectApplyListMine(RecruitApplyVO vo) {
        vo.setUserId(currentUserId());
        return commonDAO.selectList("recruitDAO.selectApplyListMine", vo);
    }

    /** 참여 신청(로그인 회원). 마감 모집·본인 모집·중복 신청 차단. */
    public void applyInsert(RecruitApplyVO vo) {
        String me = currentUserId();
        RecruitVO key = new RecruitVO();
        key.setDbKey(vo.getRecruitId());
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        if (recruit == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "모집을 찾을 수 없습니다.");
        }
        if ("RECRUIT02".equals(recruit.getStatusCd())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "마감된 모집입니다.");
        }
        if (me.equals(recruit.getRegId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인이 등록한 모집에는 신청할 수 없습니다.");
        }
        Integer dup = commonDAO.selectOne("recruitDAO.selectApplyCountByUser", applyKey(vo.getRecruitId(), me));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 신청한 모집입니다.");
        }
        vo.setUserId(me);
        commonDAO.insert("recruitDAO.insertApply", vo);
    }

    /** 신청 수락/거절 — 대상 모집 주최자·관리자만. */
    public void applyUpdate(RecruitApplyVO vo) {
        RecruitApplyVO apply = commonDAO.selectOne("recruitDAO.selectApplyView", vo);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "신청을 찾을 수 없습니다.");
        }
        assertOwner(apply.getRecruitId());
        commonDAO.update("recruitDAO.updateApplyStatus", vo);
    }

    /** 신청 취소 — 신청자 본인·관리자만. */
    public void applyDelete(RecruitApplyVO vo) {
        RecruitApplyVO apply = commonDAO.selectOne("recruitDAO.selectApplyView", vo);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "신청을 찾을 수 없습니다.");
        }
        SecurityUtil.assertOwnerOrAdmin(apply.getUserId());
        commonDAO.delete("recruitDAO.deleteApply", vo);
    }

    // ===== 공통 인가 =====
    /** 모집 주최자 또는 관리자만 통과. 없으면 예외. */
    private void assertOwner(String recruitId) {
        RecruitVO key = new RecruitVO();
        key.setDbKey(recruitId);
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        if (recruit == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "모집을 찾을 수 없습니다.");
        }
        SecurityUtil.assertOwnerOrAdmin(recruit.getRegId());
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }

    private RecruitApplyVO applyKey(String recruitId, String userId) {
        RecruitApplyVO v = new RecruitApplyVO();
        v.setRecruitId(recruitId);
        v.setUserId(userId);
        return v;
    }
}
