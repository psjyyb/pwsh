package com.pwsh.domain.notification.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 인앱 알림(단일 @Service). 조회/읽음은 항상 로그인 본인 기준(user_id 서버 강제).
 * 생성({@link #notify})은 다른 도메인 서비스가 이벤트 발생 시 호출 — 수신자를 명시하고 본인 행동엔 적재하지 않음.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final CommonDAO commonDAO;

    /** 이벤트 알림 적재. 수신자==행위자(본인)면 스킵. 알림 실패가 원 트랜잭션을 막지 않도록 호출부는 부수효과로만 사용. */
    public void notify(String userId, String type, String content, String linkUrl) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String actor = SecurityUtil.getCurrentUserId();
        if (userId.equals(actor)) {
            return; // 본인 행동엔 알림 없음(내 글에 내가 댓글 등)
        }
        NotificationVO vo = new NotificationVO();
        vo.setUserId(userId);
        vo.setNotiType(type);
        vo.setContent(content);
        vo.setLinkUrl(linkUrl);
        commonDAO.insert("notificationDAO.insert", vo);
    }

    public List<NotificationVO> selectMyList() {
        NotificationVO vo = new NotificationVO();
        vo.setUserId(currentUserId());
        return commonDAO.selectList("notificationDAO.selectMyList", vo);
    }

    public int unreadCount() {
        NotificationVO vo = new NotificationVO();
        vo.setUserId(currentUserId());
        return commonDAO.selectOne("notificationDAO.selectUnreadCnt", vo);
    }

    /** 단건 읽음 — 본인 것만(user_id 조건으로 IDOR 차단). */
    public void markRead(String notiId) {
        NotificationVO vo = new NotificationVO();
        vo.setDbKey(notiId);
        vo.setUserId(currentUserId());
        commonDAO.update("notificationDAO.markRead", vo);
    }

    public void markAllRead() {
        NotificationVO vo = new NotificationVO();
        vo.setUserId(currentUserId());
        commonDAO.update("notificationDAO.markAllRead", vo);
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }
}
