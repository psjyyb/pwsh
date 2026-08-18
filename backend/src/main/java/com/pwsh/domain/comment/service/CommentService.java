package com.pwsh.domain.comment.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.bbs.service.BbsVO;
import com.pwsh.domain.notification.service.NotificationService;
import com.pwsh.global.security.GenAccessGuard;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 업무 로직. 컨트롤러는 매핑만(단일 @Service). 게시글 하위라 단건뷰 없음(목록만).
 * 목록 조회는 소속 게시글의 게시판 단위 접근 인가(GenAccessGuard) 적용.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommonDAO commonDAO;
    private final GenAccessGuard genAccessGuard;
    private final NotificationService notificationService;

    public List<CommentVO> selectList(CommentVO vo) {
        genAccessGuard.checkPost(vo.getBbsId());
        String me = SecurityUtil.getCurrentUserId();
        vo.setViewerId((me == null || "system".equals(me)) ? null : me); // 좋아요 여부(liked_yn) 판정용
        return commonDAO.selectList("commentDAO.selectList", vo);
    }

    /** 등록 — 접근 불가 게시판(게시글)엔 댓글 작성 차단. 등록 후 알림(답글=부모 댓글 작성자, 아니면 글 작성자). */
    @Transactional
    public void insert(CommentVO vo) {
        genAccessGuard.checkPost(vo.getBbsId());
        commonDAO.insert("commentDAO.insert", vo);
        BbsVO key = new BbsVO();
        key.setRowId(vo.getBbsId());
        BbsVO post = commonDAO.selectOne("bbsDAO.selectView", key);
        String link = post != null ? "/gen/board/" + post.getBbsinfoId() + "?post=" + vo.getBbsId() : null;

        // @닉네임 멘션이 있으면 그 회원에게 먼저 알린다. 아래 댓글 알림과 중복되지 않게 대상을 모아 둔다.
        java.util.Set<String> mentioned = notificationService.notifyMentions(vo.getContext(), link, java.util.Set.of());

        String pid = vo.getPCommentId();
        if (pid != null && !pid.isEmpty() && !"0".equals(pid)) {
            // 대댓글 → 부모 댓글 작성자에게
            CommentVO pk = new CommentVO();
            pk.setRowId(pid);
            CommentVO parent = commonDAO.selectOne("commentDAO.selectView", pk);
            if (parent != null && !mentioned.contains(parent.getRegId())) {
                notificationService.notify(parent.getRegId(), "COMMENT", "내 댓글에 답글이 달렸어요.", link);
            }
        } else if (post != null && !mentioned.contains(post.getRegId())) {
            // 최상위 댓글 → 글 작성자에게(멘션으로 이미 알린 경우는 건너뛴다)
            notificationService.notify(post.getRegId(), "COMMENT",
                    "'" + post.getTitle() + "' 글에 새 댓글이 달렸어요.", link);
        }
    }

    /** 수정 — 작성자 본인·관리자만(IDOR 방지). */
    public void update(CommentVO vo) {
        loadForModify(vo);
        commonDAO.update("commentDAO.update", vo);
    }

    /** 삭제(논리) — 작성자 본인·관리자만. */
    public void delete(CommentVO vo) {
        loadForModify(vo);
        commonDAO.delete("commentDAO.delete", vo);
    }

    /** 수정/삭제 공통: 대상 로드 + 작성자/관리자 인가. */
    private void loadForModify(CommentVO vo) {
        CommentVO cmt = commonDAO.selectOne("commentDAO.selectView", vo);
        if (cmt == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        SecurityUtil.assertOwnerOrAdmin(cmt.getRegId());
    }
}
