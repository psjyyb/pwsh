package com.pwsh.domain.notification.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인앱 알림(단일 @Service). 조회/읽음은 항상 로그인 본인 기준(user_id 서버 강제).
 * 생성({@link #notify})은 다른 도메인 서비스가 이벤트 발생 시 호출 — 수신자를 명시하고 본인 행동엔 적재하지 않음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final CommonDAO commonDAO;
    private final com.pwsh.global.realtime.RealtimeService realtimeService;

    /**
     * 이벤트 알림 적재. 수신자==행위자(본인)면 스킵.
     * REQUIRES_NEW + best-effort: 알림 적재 실패가 원(호출자) 트랜잭션(댓글/신청 등록 등)을 롤백시키지 않도록
     * 독립 트랜잭션으로 처리하고 예외는 삼킨다(순수 부수효과).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(String userId, String type, String content, String linkUrl) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String actor = SecurityUtil.getCurrentUserId();
        if (userId.equals(actor)) {
            return; // 본인 행동엔 알림 없음(내 글에 내가 댓글 등)
        }
        if (!isEnabled(userId, type)) {
            return; // 수신자가 해당 유형 알림을 꺼둠(t_noti_setting)
        }
        try {
            NotificationVO vo = new NotificationVO();
            vo.setUserId(userId);
            vo.setNotiType(type);
            vo.setContent(content);
            vo.setLinkUrl(linkUrl);
            commonDAO.insert("notificationDAO.insert", vo);
            // 실시간: 접속 중이면 헤더 배지가 즉시 갱신되도록 종류만 밀어준다
            realtimeService.push(userId, "notification");
        } catch (Exception e) {
            // 알림 실패는 원 동작을 막지 않는다(독립 트랜잭션이라 호출자 롤백과도 무관).
            // 다만 조용히 사라지면 원인 추적이 불가능하므로 경고는 남긴다.
            log.warn("[Notification] 적재 실패: user={}, type={} ({})", userId, type, e.getMessage());
        }
    }

    /**
     * 수신자가 이 유형의 알림을 받도록 설정했는지. 설정 행이 없으면 기본 수신(Y).
     * 유형 매핑: APPLY/ACCEPT/REJECT/REMIND/NEWRECRUIT→notiApply, COMMENT→notiComment,
     * MESSAGE→notiMessage, REVIEW→notiReview.
     * (REMIND=모임 하루 전 리마인더, NEWRECRUIT=담은 취미에 새 모집 —
     *  둘 다 모임 관련이라 '모임 알림' 설정을 함께 따른다. 설정을 더 쪼개려면 t_noti_setting에 컬럼 추가가 필요하다)
     */
    private boolean isEnabled(String userId, String type) {
        try {
            NotificationVO p = new NotificationVO();
            p.setUserId(userId);
            NotificationVO s = commonDAO.selectOne("notificationDAO.selectSetting", p);
            if (s == null) {
                return true;
            }
            if ("COMMENT".equals(type)) {
                return !"N".equals(s.getNotiComment());
            }
            if ("MESSAGE".equals(type)) {
                return !"N".equals(s.getNotiMessage());
            }
            if ("REVIEW".equals(type)) {
                return !"N".equals(s.getNotiReview());
            }
            return !"N".equals(s.getNotiApply()); // APPLY/ACCEPT/REJECT/REMIND/NEWRECRUIT 등 모임 관련
        } catch (Exception e) {
            return true; // 설정 조회 실패 시 알림을 막지 않는다
        }
    }

    /** 내 알림 수신 설정 조회(행 없으면 전부 Y). */
    public NotificationVO selectMySetting() {
        NotificationVO p = new NotificationVO();
        p.setUserId(currentUserId());
        return commonDAO.selectOne("notificationDAO.selectSetting", p);
    }

    /** 내 알림 수신 설정 저장(upsert). */
    @Transactional
    public void saveMySetting(NotificationVO req) {
        NotificationVO p = new NotificationVO();
        p.setUserId(currentUserId());
        p.setNotiApply(normalize(req.getNotiApply()));
        p.setNotiComment(normalize(req.getNotiComment()));
        p.setNotiMessage(normalize(req.getNotiMessage()));
        p.setNotiReview(normalize(req.getNotiReview()));
        commonDAO.insert("notificationDAO.upsertSetting", p);
    }

    /** 'N'만 끔으로 인정, 그 외(널·공백·Y)는 켬. */
    private String normalize(String v) {
        return "N".equals(v) ? "N" : "Y";
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
        // 숫자 검증: ::integer 캐스트가 DB 오류(500)로 터지지 않도록 400으로 선차단
        if (notiId == null || !notiId.matches("\\d+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 알림입니다.");
        }
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
