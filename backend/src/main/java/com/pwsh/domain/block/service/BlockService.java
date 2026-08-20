package com.pwsh.domain.block.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.member.service.MemberVO;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 차단(단일 @Service). 단방향 — 내가 차단하면 상대는 나에게 쪽지를 보낼 수 없고,
 * 상대의 글·댓글은 내 화면에서 가려진다(상대 화면은 그대로).
 * member_id(주체)는 항상 서버가 강제한다.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final CommonDAO commonDAO;
    private final com.pwsh.global.security.HandleResolver handleResolver;

    /** 차단 토글(대상은 handle) → 결과 상태(blockedYn). */
    @Transactional
    public BlockVO toggle(String blockedHandle) {
        String me = currentMemberId();
        String blockedId = handleResolver.toMemberId(blockedHandle); // 공개 식별자 → 내부 로그인 ID(미존재 시 404)
        if (me.equals(blockedId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신은 차단할 수 없습니다.");
        }
        BlockVO key = key(me, blockedId);
        Integer active = commonDAO.selectOne("blockDAO.selectActiveCnt", key);
        boolean blocked;
        if (active != null && active > 0) {
            commonDAO.delete("blockDAO.delete", key);
            blocked = false;
        } else {
            commonDAO.insert("blockDAO.insert", key);
            blocked = true;
        }
        BlockVO r = new BlockVO();
        r.setBlockedYn(blocked ? "Y" : "N");
        return r;
    }

    /** 내가 차단한 회원 목록. */
    public List<BlockVO> selectMyList() {
        BlockVO vo = new BlockVO();
        vo.setMemberId(currentMemberId());
        return commonDAO.selectList("blockDAO.selectMyList", vo);
    }

    /** 내가 차단한 회원 ID 목록(프론트 콘텐츠 숨김 판정용). */
    public List<String> selectMyBlockedIds() {
        BlockVO vo = new BlockVO();
        vo.setMemberId(currentMemberId());
        return commonDAO.selectList("blockDAO.selectMyBlockedIds", vo);
    }

    /** 내가 특정 회원(handle)을 차단했는지(프로필 화면 버튼 상태). */
    public boolean isBlocked(String blockedHandle) {
        Integer cnt = commonDAO.selectOne("blockDAO.selectActiveCnt",
                key(currentMemberId(), handleResolver.toMemberId(blockedHandle)));
        return cnt != null && cnt > 0;
    }

    /**
     * 상대가 나를 차단했는지 — 쪽지 발송 차단 판정에 쓰인다(MessageService에서 호출).
     * 예외를 던지지 않고 boolean만 반환(호출부가 메시지를 정한다).
     */
    public boolean isBlockedBy(String myId, String otherId) {
        if (myId == null || otherId == null) {
            return false;
        }
        Integer cnt = commonDAO.selectOne("blockDAO.selectBlockedByCnt", key(myId, otherId));
        return cnt != null && cnt > 0;
    }

    private BlockVO key(String memberId, String blockedId) {
        BlockVO v = new BlockVO();
        v.setMemberId(memberId);
        v.setBlockedId(blockedId);
        return v;
    }

    private MemberVO memberIdParam(String memberId) {
        MemberVO v = new MemberVO();
        v.setMemberId(memberId);
        return v;
    }

    private String currentMemberId() {
        String me = SecurityUtil.getCurrentMemberId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }
}
