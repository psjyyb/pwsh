package com.pwsh.domain.like.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.global.security.GenAccessGuard;
import com.pwsh.global.security.SecurityUtil;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글·댓글 추천/비추천(단일 @Service).
 *
 * <p>한 사람이 한 대상에 <b>한 표</b>. 같은 쪽을 다시 누르면 취소, 반대쪽을 누르면 전환한다.
 * 표(content_like)가 원본이고 good_cnt·bad_cnt는 표시용 사본이라, 표가 <b>실제로 바뀐 만큼만</b>
 * 숫자를 움직인다 — 더블클릭처럼 같은 요청이 겹쳐도 숫자가 표와 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private static final String POST = "POST";
    private static final String COMMENT = "COMMENT";
    private static final String GOOD = "GOOD";
    private static final String BAD = "BAD";

    private final CommonDAO commonDAO;
    private final GenAccessGuard genAccessGuard;

    @Transactional
    public LikeVO vote(LikeVO vo) {
        String type = vo.getTargetType();
        if (!POST.equals(type) && !COMMENT.equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.like.invalidTarget"));
        }
        // 구분값이 없으면 추천 — 추천 버튼 하나만 둔 화면(좋아요)이 그대로 부를 수 있게
        String likeType = vo.getLikeType() == null || vo.getLikeType().isEmpty() ? GOOD : vo.getLikeType();
        if (!GOOD.equals(likeType) && !BAD.equals(likeType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.like.invalidType"));
        }
        // 매퍼의 ::integer 캐스트가 DB 오류(500)로 터지기 전에 400으로 막는다
        if (vo.getTargetId() == null || !vo.getTargetId().matches("\\d{1,9}")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.like.invalidTarget"));
        }
        vo.setLikeType(likeType);
        vo.setMemberId(currentMemberId());

        String postId = commonDAO.selectOne("likeDAO.selectTargetPostId", vo);
        if (postId == null) {
            // 삭제된 글·댓글에 표가 쌓이지 않게(고아 표 방지)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, Messages.get("error.like.targetNotFound"));
        }
        // ★ 볼 수 없는 게시판의 글엔 투표도 못 한다 — 막지 않으면 글 번호를 찍어 비공개 게시판에
        //   어떤 글이 있는지 확인하는 통로가 된다(404/200 차이).
        genAccessGuard.checkPost(postId);

        String mine = commonDAO.selectOne("likeDAO.selectMyType", vo);
        String now;
        if (mine == null) {
            now = commonDAO.insert("likeDAO.insert", vo) > 0 ? likeType : null;
            if (now != null) {
                moveCount(vo, likeType, 1);
            }
        } else if (mine.equals(likeType)) {
            // 같은 쪽을 다시 누름 → 취소
            if (commonDAO.delete("likeDAO.delete", vo) > 0) {
                moveCount(vo, likeType, -1);
            }
            now = null;
        } else {
            // 반대쪽 → 전환. 두 숫자를 함께 옮겨야 한다(한쪽만 고치면 한 사람이 두 표를 던진 것처럼 보인다)
            vo.setPrevType(mine);
            if (commonDAO.update("likeDAO.updateType", vo) > 0) {
                moveCount(vo, mine, -1);
                moveCount(vo, likeType, 1);
            }
            now = likeType;
        }

        LikeVO counts = commonDAO.selectOne("likeDAO.selectCounts", vo);
        LikeVO r = new LikeVO();
        r.setLikedYn(GOOD.equals(now) ? "Y" : "N");
        r.setDislikedYn(BAD.equals(now) ? "Y" : "N");
        r.setGoodCnt(counts == null ? "0" : counts.getGoodCnt());
        r.setBadCnt(counts == null ? "0" : counts.getBadCnt());
        return r;
    }

    private void moveCount(LikeVO vo, String likeType, int delta) {
        Map<String, Object> p = new HashMap<>();
        p.put("targetType", vo.getTargetType());
        p.put("targetId", vo.getTargetId());
        p.put("likeType", likeType);
        p.put("delta", delta);
        commonDAO.update("likeDAO.updateCount", p);
    }

    /** 누른 사람은 서버가 정한다 — 클라이언트 값을 쓰면 남의 이름으로 표를 던질 수 있다. */
    private String currentMemberId() {
        String me = SecurityUtil.getCurrentMemberId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return me;
    }
}
