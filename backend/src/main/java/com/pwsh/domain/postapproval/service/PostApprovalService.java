package com.pwsh.domain.postapproval.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.notification.service.NotificationService;
import com.pwsh.domain.post.service.PostService;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시 승인(CMS 1.2.41, 단일 @Service) — 승인을 쓰는 게시판(board.approval_yn)의 회원 글을 관리자가 승인·반려한다.
 *
 * <p>승인 대기로 넣는 쪽은 {@link PostService}다(등록·작성자 수정). 여기는 처리만 한다.
 * 사용자 결정: 반려는 사유 필수 + 작성자 인앱 알림, 반려된 글은 작성자가 고치면 다시 대기.
 */
@Service
@RequiredArgsConstructor
public class PostApprovalService {

    /** 반려 사유 최대 길이(post.approval_memo) */
    private static final int MEMO_MAX = 500;

    private final CommonDAO commonDAO;
    private final NotificationService notificationService;

    public List<PostApprovalVO> selectList(PostApprovalVO vo) {
        prepareFilter(vo);
        return commonDAO.selectList("postApprovalDAO.selectList", vo);
    }

    public int selectListTotalCount(PostApprovalVO vo) {
        prepareFilter(vo);
        return commonDAO.selectOne("postApprovalDAO.selectListTotalCount", vo);
    }

    public PostApprovalVO selectView(PostApprovalVO vo) {
        return commonDAO.selectOne("postApprovalDAO.selectView", vo);
    }

    /**
     * 승인(APPROVAL02) 또는 반려(APPROVAL03, 사유 필수). 대기 글만 처리하고, 처리하면 작성자에게 알린다(알림은 커밋 뒤).
     * 승인하면 사유를 남기지 않는다 — 작성자에게 보이는 사유는 반려 사유뿐이다.
     */
    @Transactional
    public void update(PostApprovalVO vo) {
        boolean approved = PostService.APPROVED.equals(vo.getApprovalCd());
        if (!approved && !PostService.REJECTED.equals(vo.getApprovalCd())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.postApproval.invalidStatus"));
        }
        String memo = vo.getApprovalMemo() == null ? "" : vo.getApprovalMemo().trim();
        if (!approved && memo.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.postApproval.memoRequired"));
        }
        if (memo.length() > MEMO_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.postApproval.memoTooLong", MEMO_MAX));
        }
        PostApprovalVO post = commonDAO.selectOne("postApprovalDAO.selectView", vo);
        if (post == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, Messages.get("error.post.notFound"));
        }
        vo.setApprovalMemo(approved ? null : memo);
        vo.setApprovalId(SecurityUtil.getCurrentMemberId());
        if (commonDAO.update("postApprovalDAO.updateDecision", vo) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.postApproval.notWaiting"));
        }
        notificationService.notifyPostApproval(post.getRegId(), post.getBoardId(), post.getRowId(), post.getTitle(), approved, memo);
    }

    private void prepareFilter(PostApprovalVO vo) {
        String f = vo.getFilterKeyword() == null ? "" : vo.getFilterKeyword().trim();
        vo.setKeywordLike(f.isEmpty() ? null : f.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
    }
}
