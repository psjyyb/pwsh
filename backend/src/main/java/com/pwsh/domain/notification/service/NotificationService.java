package com.pwsh.domain.notification.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 인앱 알림(단일 @Service). 조회/읽음은 항상 로그인 본인 기준(member_id 서버 강제).
 * 생성({@link #notify})은 다른 도메인 서비스가 이벤트 발생 시 호출 — 수신자를 명시하고 본인 행동엔 적재하지 않음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 내 신청(폼)의 처리상태 변경(CMS 1.2.35) — 수신 설정과 상관없이 항상 받는다(내 신청의 결과라 끌 이유가 적다) */
    public static final String FORM = "FORM";
    private static final int TITLE_MAX = 40;

    private final CommonDAO commonDAO;
    private final com.pwsh.global.realtime.RealtimeService realtimeService;
    private final PlatformTransactionManager txManager;

    /** 알림 보존기간(일). 0이면 지우지 않는다(CMS 1.2.36) */
    @Value("${notification.retention-days:90}")
    private int retentionDays;

    /**
     * 보존기간(notification.retention-days) 지난 알림 삭제 — 읽음 여부와 상관없이 만든 시각 기준(CMS 1.2.36).
     * 배치 작업 관리(BatchService, 작업 코드 NOTIFICATION_PURGE)가 DB의 주기로 부른다. 법정 보존 대상이 아니라
     * 로그 보관 정리(LOG_RETENTION)와 따로 둔다. 모집 하루 전 알림의 "오늘 보냈는지" 확인은 오늘 행만 보므로 영향이 없다.
     * @return 지운 건수
     */
    public int purgeExpired() {
        if (retentionDays <= 0) {
            return 0;
        }
        int deleted = commonDAO.delete("notificationDAO.deleteExpired", java.util.Map.of("days", String.valueOf(retentionDays)));
        if (deleted > 0) {
            log.info("알림 정리: {}건 삭제(보존 {}일)", deleted, retentionDays);
        }
        return deleted;
    }

    /**
     * 이벤트 알림 적재. 수신자==행위자(본인)면 스킵.
     *
     * <p>★ 실패가 원래 동작(댓글·신청 등록 등)을 되돌리지 않게 독립 트랜잭션으로 넣고 예외는 삼킨다(로그만).
     * <p>★ 원래 동작이 트랜잭션 안이면 <b>그게 커밋된 뒤에</b> 만든다(CMS 1.2.35) — v0.6.34까지는 호출 즉시 만들고 커밋 전에 밀어서,
     * 원래 동작이 되돌려져도 알림만 남았고, 화면이 이벤트를 받자마자 다시 읽으면 아직 안 보이는 알림이 있었다.
     */
    public void notify(String memberId, String type, String content, String linkUrl) {
        if (memberId == null || memberId.isBlank() || memberId.equals(SecurityUtil.getCurrentMemberId())) {
            return; // 본인 행동엔 알림 없음(내 글에 내가 댓글 등)
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    insertAndPush(memberId, type, content, linkUrl);
                }
            });
            return;
        }
        insertAndPush(memberId, type, content, linkUrl);
    }

    private void insertAndPush(String memberId, String type, String content, String linkUrl) {
        try {
            if (!isEnabled(memberId, type)) {
                return; // 수신자가 해당 유형 알림을 꺼둠(notification_setting)
            }
            NotificationVO vo = new NotificationVO();
            vo.setMemberId(memberId);
            vo.setType(type);
            vo.setContent(content.length() <= 300 ? content : content.substring(0, 300));
            vo.setLinkUrl(linkUrl);
            TransactionTemplate tx = new TransactionTemplate(txManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(s -> commonDAO.insert("notificationDAO.insert", vo));
            // 실시간: 접속 중이면 헤더 배지가 즉시 갱신되도록 종류만 밀어준다(행이 커밋된 뒤라 다시 읽으면 보인다)
            realtimeService.push(memberId, "notification");
        } catch (Exception e) {
            // 알림 실패는 원 동작을 막지 않는다. 다만 조용히 사라지면 원인 추적이 불가능하므로 경고는 남긴다.
            log.warn("[Notification] 적재 실패: user={}, type={} ({})", memberId, type, e.getMessage());
        }
    }

    /** 내 신청의 처리상태 변경 — 로그인해 낸 신청만(비로그인 제출은 받을 사람이 없다). 화면에 내 신청 목록이 없어 이동 주소는 없다. */
    public void notifyFormStatus(String memberId, String formTitle, String statusName) {
        notify(memberId, FORM, "신청하신 '" + cut(formTitle) + "' 처리상태가 '" + statusName + "'(으)로 바뀌었습니다.", null);
    }

    private static String cut(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= TITLE_MAX ? s : s.substring(0, TITLE_MAX) + "…";
    }

    /**
     * 수신자가 이 유형의 알림을 받도록 설정했는지. 설정 행이 없으면 기본 수신(Y).
     * 유형 매핑: APPLY/ACCEPT/REJECT/REMIND/NEWRECRUIT→applyYn, COMMENT·MENTION→commentYn,
     * MESSAGE→messageYn, REVIEW→reviewYn, FORM→항상 받음.
     * (REMIND=모임 하루 전 리마인더, NEWRECRUIT=담은 취미에 새 모집 —
     *  둘 다 모임 관련이라 '모임 알림' 설정을 함께 따른다. 설정을 더 쪼개려면 notification_setting에 컬럼 추가가 필요하다)
     */
    private boolean isEnabled(String memberId, String type) {
        if (FORM.equals(type)) {
            return true; // 아래 마지막 줄(모임 알림)로 떨어지면 모임 알림을 끈 회원이 신청 결과도 못 받는다
        }
        try {
            NotificationVO p = new NotificationVO();
            p.setMemberId(memberId);
            NotificationVO s = commonDAO.selectOne("notificationDAO.selectSetting", p);
            if (s == null) {
                return true;
            }
            if ("COMMENT".equals(type) || "MENTION".equals(type)) {
                return !"N".equals(s.getCommentYn());
            }
            if ("MESSAGE".equals(type)) {
                return !"N".equals(s.getMessageYn());
            }
            if ("REVIEW".equals(type)) {
                return !"N".equals(s.getReviewYn());
            }
            return !"N".equals(s.getApplyYn()); // APPLY/ACCEPT/REJECT/REMIND/NEWRECRUIT 등 모임 관련
        } catch (Exception e) {
            return true; // 설정 조회 실패 시 알림을 막지 않는다
        }
    }

    /**
     * 본문에서 <b>@닉네임</b>을 찾아 그 회원에게 멘션 알림.
     *
     * <p>닉네임은 정확히 일치해야 한다(부분 일치로 엉뚱한 사람을 부르지 않게). 존재하지 않는 닉네임은 그냥 무시한다.
     * 이미 다른 사유로 알림을 보낸 사람(글쓴이·부모 댓글 작성자)은 {@code already}로 걸러 중복 발송을 막는다.
     *
     * @return 멘션 알림을 보낸 회원 ID
     */
    public java.util.Set<String> notifyMentions(String content, String link, java.util.Set<String> already) {
        return notifyMentions(content, link, already, null);
    }

    /**
     * @param allowed 알릴 수 있는 회원 화이트리스트(null이면 제한 없음).
     *                비공개 대화(모임 단체 대화)에서 <b>바깥 사람을 불러들이지 않도록</b> 멤버로 제한할 때 쓴다.
     */
    public java.util.Set<String> notifyMentions(String content, String link,
                                                java.util.Set<String> already, java.util.Set<String> allowed) {
        java.util.Set<String> sent = new java.util.LinkedHashSet<>();
        java.util.Set<String> names = parseMentions(content);
        if (names.isEmpty()) {
            return sent;
        }
        List<NotificationVO> users = commonDAO.selectList("notificationDAO.selectMemberIdsByNicknames",
                java.util.Map.of("nicknames", names));
        String actor = SecurityUtil.getCurrentMemberId();
        for (NotificationVO u : users) {
            String uid = u.getMemberId();
            if (uid == null || uid.equals(actor) || already.contains(uid) || sent.contains(uid)) {
                continue;
            }
            if (allowed != null && !allowed.contains(uid)) {
                continue; // 그 대화를 볼 수 없는 사람은 멘션해도 알리지 않는다
            }
            notify(uid, "MENTION", "누군가 회원님을 언급했어요: @" + u.getNickname(), link);
            sent.add(uid);
        }
        return sent;
    }

    /** 본문에서 @뒤에 오는 닉네임 후보를 뽑는다(한글·영문·숫자·밑줄, 최대 30자 = 닉네임 컬럼 길이). */
    private java.util.Set<String> parseMentions(String content) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return names;
        }
        java.util.regex.Matcher m = MENTION.matcher(content);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static final java.util.regex.Pattern MENTION =
            java.util.regex.Pattern.compile("@([\\p{L}\\p{N}_]{1,30})");

    /** 내 알림 수신 설정 조회(행 없으면 전부 Y). */
    public NotificationVO selectMySetting() {
        NotificationVO p = new NotificationVO();
        p.setMemberId(currentMemberId());
        return commonDAO.selectOne("notificationDAO.selectSetting", p);
    }

    /** 내 알림 수신 설정 저장(upsert). */
    @Transactional
    public void saveMySetting(NotificationVO req) {
        NotificationVO p = new NotificationVO();
        p.setMemberId(currentMemberId());
        p.setApplyYn(normalize(req.getApplyYn()));
        p.setCommentYn(normalize(req.getCommentYn()));
        p.setMessageYn(normalize(req.getMessageYn()));
        p.setReviewYn(normalize(req.getReviewYn()));
        commonDAO.insert("notificationDAO.upsertSetting", p);
    }

    /** 'N'만 끔으로 인정, 그 외(널·공백·Y)는 켬. */
    private String normalize(String v) {
        return "N".equals(v) ? "N" : "Y";
    }

    public List<NotificationVO> selectMyList() {
        NotificationVO vo = new NotificationVO();
        vo.setMemberId(currentMemberId());
        return commonDAO.selectList("notificationDAO.selectMyList", vo);
    }

    public int unreadCount() {
        NotificationVO vo = new NotificationVO();
        vo.setMemberId(currentMemberId());
        return commonDAO.selectOne("notificationDAO.selectUnreadCnt", vo);
    }

    /** 단건 읽음 — 본인 것만(member_id 조건으로 IDOR 차단). */
    public void markRead(String notificationId) {
        // 숫자 검증: ::integer 캐스트가 DB 오류(500)로 터지지 않도록 400으로 선차단
        if (notificationId == null || !notificationId.matches("\\d+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.notification.invalid"));
        }
        NotificationVO vo = new NotificationVO();
        vo.setRowId(notificationId);
        vo.setMemberId(currentMemberId());
        commonDAO.update("notificationDAO.markRead", vo);
    }

    public void markAllRead() {
        NotificationVO vo = new NotificationVO();
        vo.setMemberId(currentMemberId());
        commonDAO.update("notificationDAO.markAllRead", vo);
    }

    private String currentMemberId() {
        String me = SecurityUtil.getCurrentMemberId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, Messages.get("error.common.loginRequired"));
        }
        return me;
    }
}
