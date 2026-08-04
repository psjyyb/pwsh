package com.pwsh.domain.bookmark.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 북마크(스크랩) 단일 @Service. user_id는 서버가 강제(위변조 차단).
 * 토글: 없으면 추가, 있으면 제거. 목록은 대상이 살아있는 것만(삭제 콘텐츠 제외).
 */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final CommonDAO commonDAO;

    /** 북마크 토글 → 결과 상태(markedYn) 반환. */
    @Transactional
    public BookmarkVO toggle(String targetType, String targetId) {
        if (!"BBS".equals(targetType) && !"RECRUIT".equals(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 대상 유형입니다.");
        }
        if (targetId == null || !targetId.matches("\\d+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 대상입니다.");
        }
        BookmarkVO key = new BookmarkVO();
        key.setUserId(currentUserId());
        key.setTargetType(targetType);
        key.setTargetId(targetId);

        Integer active = commonDAO.selectOne("bookmarkDAO.selectActiveCnt", key);
        boolean marked;
        if (active != null && active > 0) {
            commonDAO.delete("bookmarkDAO.delete", key);
            marked = false;
        } else {
            commonDAO.insert("bookmarkDAO.insert", key);
            marked = true;
        }
        BookmarkVO r = new BookmarkVO();
        r.setMarkedYn(marked ? "Y" : "N");
        return r;
    }

    /** 내 북마크 목록 — type='BBS'면 게시글, 그 외 모집. */
    public List<BookmarkVO> selectMyList(String targetType) {
        BookmarkVO vo = new BookmarkVO();
        vo.setUserId(currentUserId());
        return "BBS".equals(targetType)
                ? commonDAO.selectList("bookmarkDAO.selectMyBbsList", vo)
                : commonDAO.selectList("bookmarkDAO.selectMyRecruitList", vo);
    }

    /** 내가 북마크한 대상 id 목록(목록 화면 하트 표시용). */
    public List<String> selectMyIds(String targetType) {
        BookmarkVO vo = new BookmarkVO();
        vo.setUserId(currentUserId());
        vo.setTargetType("BBS".equals(targetType) ? "BBS" : "RECRUIT");
        return commonDAO.selectList("bookmarkDAO.selectMyIdsByType", vo);
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }
}
