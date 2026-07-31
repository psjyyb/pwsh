package com.pwsh.domain.comment.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.GenAccessGuard;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 댓글 업무 로직. 컨트롤러는 매핑만(단일 @Service). 게시글 하위라 단건뷰 없음(목록만).
 * 목록 조회는 소속 게시글의 게시판 단위 접근 인가(GenAccessGuard) 적용.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommonDAO commonDAO;
    private final GenAccessGuard genAccessGuard;

    public List<CommentVO> selectList(CommentVO vo) {
        genAccessGuard.checkPost(vo.getBbsId());
        return commonDAO.selectList("commentDAO.selectList", vo);
    }

    /** 등록 — 접근 불가 게시판(게시글)엔 댓글 작성 차단. */
    public void insert(CommentVO vo) {
        genAccessGuard.checkPost(vo.getBbsId());
        commonDAO.insert("commentDAO.insert", vo);
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
