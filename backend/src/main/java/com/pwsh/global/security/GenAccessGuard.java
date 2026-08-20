package com.pwsh.global.security;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * GEN(사용자) 콘텐츠 단위 접근 인가.
 * 콘텐츠(게시판/페이지)는 그것을 노출하는 GEN 메뉴의 그룹 권한으로 열람 가부를 판정한다.
 * - 실효그룹: 로그인=본인 auth_member 그룹, 비로그인=GUEST.
 * - 관리자(typeCd=MEM02)·부트스트랩 admin은 콘텐츠 관리를 위해 통과.
 * 사이드바 메뉴 단위 노출(selectMenuTree)과 별개로, board/page id 직접 딥링크까지 차단한다.
 */
@Component
@RequiredArgsConstructor
public class GenAccessGuard {

    private final CommonDAO commonDAO;

    /** 게시판(MENU02) 접근 인가. boardId = board PK.
     *  취미(hobby)에 연결된 게시판은 커뮤니티 공개 게시판이므로 메뉴 권한과 무관하게 열람 허용(쓰기는 로그인 필요). */
    public void checkBoard(String boardId) {
        if (isHobbyBoard(boardId)) {
            return;
        }
        checkContent("MENU02", boardId);
    }

    /** 해당 게시판이 어떤 취미의 연결 게시판인지 */
    private boolean isHobbyBoard(String boardId) {
        if (boardId == null || boardId.isEmpty()) {
            return false;
        }
        Map<String, Object> p = new HashMap<>();
        p.put("boardId", boardId);
        Integer cnt = commonDAO.selectOne("hobbyDAO.countByBoard", p);
        return cnt != null && cnt > 0;
    }

    /** 페이지(MENU03) 접근 인가. pageId = page PK */
    public void checkPage(String pageId) {
        checkContent("MENU03", pageId);
    }

    /** 댓글 접근 인가: 게시글(postId)이 속한 게시판 권한으로 판정. 없으면 403. */
    public void checkPost(String postId) {
        if (postId == null || postId.isEmpty()) {
            return;
        }
        if (!canAccessPost(postId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 게시글(postId)이 속한 게시판에 접근 가능한지(예외 없이 boolean). 이미지/다운로드 인가에서 재사용. */
    public boolean canAccessPost(String postId) {
        if (postId == null || postId.isEmpty()) {
            return false;
        }
        if (isPrivileged()) {
            return true;
        }
        // 취미(hobby) 연결 게시판의 글은 커뮤니티 공개 — checkBoard와 동일 규칙(메뉴 권한 무관).
        Map<String, Object> bk = new HashMap<>();
        bk.put("postId", postId);
        String boardId = commonDAO.selectOne("postDAO.selectBoardIdByPostId", bk);
        if (isHobbyBoard(boardId)) {
            return true;
        }
        Map<String, Object> p = new HashMap<>();
        p.put("postId", postId);
        p.put("memberId", effectiveMemberId());
        Integer cnt = commonDAO.selectOne("menuDAO.countGenBoardPermByPost", p);
        return cnt != null && cnt > 0;
    }

    private void checkContent(String connCd, String connId) {
        if (connId == null || connId.isEmpty() || isPrivileged()) {
            return;
        }
        Map<String, Object> p = new HashMap<>();
        p.put("connCd", connCd);
        p.put("connId", connId);
        p.put("memberId", effectiveMemberId());
        deny(commonDAO.selectOne("menuDAO.countGenContentPerm", p));
    }

    private void deny(Integer cnt) {
        if (cnt == null || cnt == 0) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 관리자(MEM02)·부트스트랩 admin은 콘텐츠 인가 면제(관리 목적 조회). */
    private boolean isPrivileged() {
        return SecurityUtil.isAdmin();
    }

    /** 로그인 사용자면 memberId, 비로그인('system'/null)이면 null(→ GUEST 그룹 기준). */
    private String effectiveMemberId() {
        String memberId = SecurityUtil.getCurrentMemberId();
        return (memberId == null || "system".equals(memberId)) ? null : memberId;
    }
}
