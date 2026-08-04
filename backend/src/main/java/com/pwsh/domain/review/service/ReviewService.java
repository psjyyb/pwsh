package com.pwsh.domain.review.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.notification.service.NotificationService;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 후기·평점(단일 @Service).
 * 작성 자격: 종료된 모임(마감 또는 모임일 경과)에서 '함께한 사람'(주최자 + 수락된 참여자)끼리 상호 1회.
 * 조회(받은 후기·평균)는 공개, 작성/삭제는 로그인 본인.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final CommonDAO commonDAO;
    private final NotificationService notificationService;
    private final com.pwsh.global.security.HandleResolver handleResolver;

    /** 회원이 받은 후기 목록(공개). 대상은 handle로 지정. */
    public List<ReviewVO> selectListByTarget(String targetHandle) {
        ReviewVO vo = new ReviewVO();
        vo.setTargetId(handleResolver.toUserId(targetHandle));
        return commonDAO.selectList("reviewDAO.selectListByTarget", vo);
    }

    /** 회원의 평균 별점·후기 수(공개). 대상은 handle로 지정. 후기 없으면 avgRating=null, reviewCnt='0'. */
    public ReviewVO selectStats(String targetHandle) {
        ReviewVO vo = new ReviewVO();
        vo.setTargetId(handleResolver.toUserId(targetHandle));
        return commonDAO.selectOne("reviewDAO.selectStatsByTarget", vo);
    }

    /** 내가 후기를 쓸 수 있는 대상 목록(종료된 내 모임의 함께한 사람 + 작성여부). */
    public List<ReviewVO> selectMyTargets() {
        ReviewVO vo = new ReviewVO();
        vo.setRegId(currentUserId());
        return commonDAO.selectList("reviewDAO.selectTargets", vo);
    }

    /** 후기 등록 — 자격·중복·별점 검증 후 저장. 대상에게 알림. */
    @Transactional
    public void insert(ReviewVO req) {
        String me = currentUserId();
        if (req.getRecruitId() == null || req.getRecruitId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "모임 정보가 없습니다.");
        }
        if (req.getTargetHandle() == null || req.getTargetHandle().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 회원이 없습니다.");
        }
        req.setTargetId(handleResolver.toUserId(req.getTargetHandle())); // 공개 식별자 → 내부 로그인 ID
        if (me.equals(req.getTargetId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인에게는 후기를 쓸 수 없습니다.");
        }
        int rating = parseRating(req.getRating());
        if (rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "별점은 1~5 사이여야 합니다.");
        }
        ReviewVO chk = new ReviewVO();
        chk.setRecruitId(req.getRecruitId());
        chk.setTargetId(req.getTargetId());
        chk.setRegId(me);
        Integer eligible = commonDAO.selectOne("reviewDAO.selectEligibleCnt", chk);
        if (eligible == null || eligible == 0) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "종료된 모임에서 함께한 회원에게만 후기를 쓸 수 있습니다.");
        }
        Integer dup = commonDAO.selectOne("reviewDAO.selectDupCnt", chk);
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 이 모임에서 후기를 작성했습니다.");
        }
        ReviewVO ins = new ReviewVO();
        ins.setRecruitId(req.getRecruitId());
        ins.setTargetId(req.getTargetId());
        ins.setRating(String.valueOf(rating));
        ins.setContent(req.getContent() == null ? "" : req.getContent().trim());
        commonDAO.insert("reviewDAO.insert", ins);
        // 링크는 대상의 handle로 — 저장되는 값이라 로그인 ID를 넣으면 알림 목록에서 계속 노출된다
        notificationService.notify(req.getTargetId(), "REVIEW",
                "모임 후기가 도착했어요. (별점 " + rating + "점)", "/gen/user/" + req.getTargetHandle());
    }

    /** 후기 삭제(논리) — 작성자 본인·관리자만. */
    @Transactional
    public void delete(ReviewVO vo) {
        ReviewVO r = commonDAO.selectOne("reviewDAO.selectView", vo);
        if (r == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "후기를 찾을 수 없습니다.");
        }
        SecurityUtil.assertOwnerOrAdmin(r.getRegId());
        commonDAO.update("reviewDAO.delete", vo);
    }

    private int parseRating(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
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
