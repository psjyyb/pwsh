package com.pwsh.domain.message.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
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

    /** 내 대화 목록(상대별 최근 + 안읽음). */
    public List<MessageVO> selectConvList() {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        return commonDAO.selectList("messageDAO.selectConvList", vo);
    }

    /** 특정 상대와의 대화 — 열람 시 안읽음을 읽음처리. */
    @Transactional
    public List<MessageVO> selectThread(String otherId) {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        vo.setOtherId(otherId);
        commonDAO.update("messageDAO.markThreadRead", vo);
        return commonDAO.selectList("messageDAO.selectThread", vo);
    }

    /** 전체 안읽음 수(헤더 배지). */
    public int unreadCnt() {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        return commonDAO.selectOne("messageDAO.selectUnreadCnt", vo);
    }

    /** 쪽지 보내기 — 발신자=현재 사용자 강제, 본인/미존재 수신자 차단. 수신자에게 알림. */
    @Transactional
    public void send(MessageVO req) {
        String me = currentUserId();
        String receiver = req.getReceiverId();
        if (receiver == null || receiver.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "받는 사람이 없습니다.");
        }
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
        MessageVO ins = new MessageVO();
        ins.setSenderId(me);
        ins.setReceiverId(receiver);
        ins.setContent(req.getContent().trim());
        commonDAO.insert("messageDAO.insert", ins);
        // 수신자 알림(발신자 닉네임 표시)
        UserVO meU = commonDAO.selectOne("userDAO.selectByUserId", userIdParam(me));
        String nm = (meU != null && meU.getNickname() != null) ? meU.getNickname() : me;
        notificationService.notify(receiver, "MESSAGE", "'" + nm + "'님이 쪽지를 보냈어요.", "/gen/message?with=" + me);
    }

    /** 상대와의 대화 읽음 처리. */
    public void markRead(String otherId) {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        vo.setOtherId(otherId);
        commonDAO.update("messageDAO.markThreadRead", vo);
    }

    /** 대화 삭제(내 화면에서만). */
    public void deleteConv(String otherId) {
        MessageVO vo = new MessageVO();
        vo.setMyId(currentUserId());
        vo.setOtherId(otherId);
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
