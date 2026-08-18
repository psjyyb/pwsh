package com.pwsh.domain.follow.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 팔로우(단일 @Service). 차단(BlockService)과 같은 단방향 모델이고, 주체는 항상 로그인 본인이다.
 * 팔로우하면 그 사람이 새 모집을 열 때 알림을 받고, 내 피드에 그 사람의 글·모집이 함께 뜬다.
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final CommonDAO commonDAO;
    private final com.pwsh.global.security.HandleResolver handleResolver;

    /** 팔로우 토글(대상은 handle) → 결과 상태(followedYn). */
    @Transactional
    public FollowVO toggle(String followeeHandle) {
        String me = currentUserId();
        String followeeId = handleResolver.toUserId(followeeHandle); // 공개 식별자 → 내부 ID(미존재 시 404)
        if (me.equals(followeeId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신은 팔로우할 수 없습니다.");
        }
        FollowVO key = key(me, followeeId);
        Integer active = commonDAO.selectOne("followDAO.selectActiveCnt", key);
        boolean followed;
        if (active != null && active > 0) {
            commonDAO.delete("followDAO.delete", key);
            followed = false;
        } else {
            commonDAO.insert("followDAO.insert", key);
            followed = true;
        }
        FollowVO r = new FollowVO();
        r.setFollowedYn(followed ? "Y" : "N");
        return r;
    }

    /** 내가 팔로우한 회원 목록. */
    public List<FollowVO> selectFollowingList() {
        FollowVO vo = new FollowVO();
        vo.setUserId(currentUserId());
        return commonDAO.selectList("followDAO.selectFollowingList", vo);
    }

    /** 나를 팔로우한 회원 목록. */
    public List<FollowVO> selectFollowerList() {
        FollowVO vo = new FollowVO();
        vo.setUserId(currentUserId());
        return commonDAO.selectList("followDAO.selectFollowerList", vo);
    }

    /** 내가 특정 회원(handle)을 팔로우했는지 — 프로필 버튼 상태. */
    public boolean isFollowing(String followeeHandle) {
        Integer cnt = commonDAO.selectOne("followDAO.selectActiveCnt",
                key(currentUserId(), handleResolver.toUserId(followeeHandle)));
        return cnt != null && cnt > 0;
    }

    /** 특정 회원(handle)의 팔로워/팔로잉 수 — 공개 프로필에 표시(비로그인도 조회 가능). */
    public FollowVO selectCounts(String followeeHandle) {
        FollowVO key = new FollowVO();
        key.setFolloweeId(handleResolver.toUserId(followeeHandle));
        return commonDAO.selectOne("followDAO.selectCounts", key);
    }

    /**
     * 나를 팔로우한 회원 ID 목록 — 새 모집 알림 대상(RecruitService에서 호출).
     * 예외 없이 목록만 돌려준다(호출부가 차단 여부·중복을 걸러 쓴다).
     */
    public List<String> selectFollowerIds(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return commonDAO.selectList("followDAO.selectFollowerIds", Map.of("userId", userId));
    }

    private FollowVO key(String userId, String followeeId) {
        FollowVO v = new FollowVO();
        v.setUserId(userId);
        v.setFolloweeId(followeeId);
        return v;
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }
}
