package com.pwsh.domain.feed.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 내 취미 피드(단일 @Service). 담은 취미(t_user_hobby) 기준으로 게시글·모집을 모아 보여준다.
 * 항상 로그인 본인 기준 — viewerId를 서버에서 강제 세팅해 남의 피드를 조회할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private final CommonDAO commonDAO;

    /** 피드 목록 — 최신순(게시글·모집 통합). */
    public List<FeedVO> selectList(FeedVO vo) {
        prepare(vo);
        return commonDAO.selectList("feedDAO.selectList", vo);
    }

    /** 피드 총건수 — 목록과 동일 조건. */
    public int selectListTotCnt(FeedVO vo) {
        prepare(vo);
        return commonDAO.selectOne("feedDAO.selectListTotCnt", vo);
    }

    /** 담은 취미 수 — 0이면 피드가 비는 이유(취미를 담지 않음)를 화면에서 안내한다. */
    public int selectMyHobbyCnt(FeedVO vo) {
        prepare(vo);
        return commonDAO.selectOne("feedDAO.selectMyHobbyCnt", vo);
    }

    /** 조회자 강제 세팅 + 필터 화이트리스트. */
    private void prepare(FeedVO vo) {
        vo.setViewerId(currentUserId());
        String f = vo.getFeedFilter();
        if (f != null && !f.isBlank() && !"BBS".equals(f) && !"RECRUIT".equals(f)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 피드 구분입니다.");
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
