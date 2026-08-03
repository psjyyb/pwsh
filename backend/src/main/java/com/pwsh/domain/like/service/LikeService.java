package com.pwsh.domain.like.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요(단일 @Service). 1인 1표 토글 — 누르면 추가(+1), 다시 누르면 취소(-1).
 * user_id는 서버가 강제(위변조 차단). good_cnt는 대상 테이블(t_bbs/t_comment)에 트랜잭션 동기화.
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private final CommonDAO commonDAO;

    @Transactional
    public LikeVO toggle(String targetType, String targetId) {
        if (!"BBS".equals(targetType) && !"COMMENT".equals(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 대상 유형입니다.");
        }
        boolean bbs = "BBS".equals(targetType);
        LikeVO key = new LikeVO();
        key.setUserId(currentUserId());
        key.setTargetType(targetType);
        key.setTargetId(targetId);

        Integer active = commonDAO.selectOne("likeDAO.selectActiveCnt", key);
        boolean nowLiked;
        if (active != null && active > 0) { // 이미 눌렀음 → 취소
            commonDAO.delete("likeDAO.delete", key);
            commonDAO.update(bbs ? "likeDAO.decBbsGood" : "likeDAO.decCommentGood", key);
            nowLiked = false;
        } else { // 좋아요 추가
            commonDAO.insert("likeDAO.insert", key);
            commonDAO.update(bbs ? "likeDAO.incBbsGood" : "likeDAO.incCommentGood", key);
            nowLiked = true;
        }
        Integer good = commonDAO.selectOne(bbs ? "likeDAO.selectBbsGood" : "likeDAO.selectCommentGood", key);

        LikeVO r = new LikeVO();
        r.setLikedYn(nowLiked ? "Y" : "N");
        r.setGoodCnt(String.valueOf(good == null ? 0 : good));
        return r;
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }
}
