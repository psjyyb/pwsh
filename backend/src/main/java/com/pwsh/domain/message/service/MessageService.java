package com.pwsh.domain.message.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.block.service.BlockService;
import com.pwsh.domain.notification.service.NotificationService;
import com.pwsh.domain.user.service.UserVO;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쪽지(1:1 메시지) 업무 로직(단일 @Service). 모든 조회/변경은 로그인 본인(myId=현재 사용자) 기준으로 서버가 강제.
 * - 대화 목록/스레드 조회, 안읽음 수, 보내기, 읽음처리, 대화 삭제(내 화면만).
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final CommonDAO commonDAO;
    private final NotificationService notificationService;
    private final BlockService blockService;
    private final com.pwsh.global.security.HandleResolver handleResolver;

    /** 내 대화 목록(상대별 최근 + 안읽음). */
    public List<MessageVO> selectConvList() {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        return commonDAO.selectList("messageDAO.selectConvList", vo);
    }

    /** 특정 상대(handle)와의 대화 — 열람 시 안읽음을 읽음처리. */
    @Transactional
    public List<MessageVO> selectThread(String otherHandle) {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        vo.setOtherId(handleResolver.toUserId(otherHandle)); // 공개 식별자 → 내부 로그인 ID
        commonDAO.update("messageDAO.markThreadRead", vo);
        return commonDAO.selectList("messageDAO.selectThread", vo);
    }

    /** 전체 안읽음 수(헤더 배지). */
    public int unreadCnt() {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        return commonDAO.selectOne("messageDAO.selectUnreadCnt", vo);
    }

    /** 쪽지 보내기 — 받는 사람은 handle로 지정. 발신자=현재 사용자 강제, 본인/미존재 수신자 차단. */
    @Transactional
    public void send(MessageVO req) {
        String me = currentUserId();
        if (req.getReceiverHandle() == null || req.getReceiverHandle().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "받는 사람이 없습니다.");
        }
        String receiver = handleResolver.toUserId(req.getReceiverHandle()); // 공개 식별자 → 내부 로그인 ID
        if (me.equals(receiver)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신에게는 보낼 수 없습니다.");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "내용을 입력해 주세요.");
        }
        UserVO r = commonDAO.selectOne("userDAO.selectByUserId", userIdParam(receiver));
        if (r == null || !"Y".equals(r.getUseYn())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "받는 회원을 찾을 수 없습니다.");
        }
        // 상대가 나를 차단했으면 발송 차단(차단 사실은 노출하지 않는 일반 문구)
        if (blockService.isBlockedBy(me, receiver)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "이 회원에게는 쪽지를 보낼 수 없습니다.");
        }
        MessageVO ins = new MessageVO();
        ins.setSenderId(me);
        ins.setReceiverId(receiver);
        ins.setContent(req.getContent().trim());
        commonDAO.insert("messageDAO.insert", ins);
        // 수신자 알림(발신자 닉네임 표시)
        UserVO meU = commonDAO.selectOne("userDAO.selectByUserId", userIdParam(me));
        String nm = (meU != null && meU.getNickname() != null) ? meU.getNickname() : me;
        // 링크는 내 handle로 — 저장되는 값이라 로그인 ID를 넣으면 알림 목록에서 계속 노출된다
        String myHandle = (meU != null) ? meU.getHandle() : null;
        notificationService.notify(receiver, "MESSAGE", "'" + nm + "'님이 쪽지를 보냈어요.",
                myHandle != null ? "/gen/message?with=" + myHandle : "/gen/message");
    }

    /** 상대(handle)와의 대화 읽음 처리. */
    public void markRead(String otherHandle) {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        vo.setOtherId(handleResolver.toUserId(otherHandle));
        commonDAO.update("messageDAO.markThreadRead", vo);
    }

    /** 대화 삭제(내 화면에서만). 상대는 handle로 지정. */
    public void deleteConv(String otherHandle) {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        vo.setOtherId(handleResolver.toUserId(otherHandle));
        commonDAO.update("messageDAO.deleteConv", vo);
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }

    private UserVO userIdParam(String userId) {
        UserVO v = new UserVO();
        v.setUserId(userId);
        return v;
    }
}
