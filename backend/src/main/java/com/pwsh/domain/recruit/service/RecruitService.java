package com.pwsh.domain.recruit.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집(취미 모임원 모집) + 참여신청 업무 로직. 컨트롤러는 매핑만(단일 @Service).
 * - 조회(목록/상세)는 공개(SecurityConfig permitAll), 쓰기/신청/수락은 로그인 필요.
 * - 모집 수정/삭제·마감·신청 수락/거절 = 주최자 또는 관리자(assertOwnerOrAdmin).
 * - 신청 취소 = 신청자 본인 또는 관리자.
 * - 모임 하루 전 리마인더는 스케줄 배치({@link #scheduledMeetReminder()}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitService {

    private final CommonDAO commonDAO;
    private final com.pwsh.domain.notification.service.NotificationService notificationService;

    // ===== 모집 =====
    public List<RecruitVO> selectList(RecruitVO vo) {
        vo.setViewerId(viewerId()); // mine_yn(내가 연 모집) 판정용
        return commonDAO.selectList("recruitDAO.selectList", vo);
    }

    /** mine_yn 판정용 현재 조회자 — 비로그인/시스템은 null. */
    private String viewerId() {
        String me = SecurityUtil.getCurrentUserId();
        return (me == null || "system".equals(me)) ? null : me;
    }

    public int selectListTotCnt(RecruitVO vo) {
        vo.setViewerId(viewerId()); // 목록과 동일한 차단 필터를 적용해 총건수 일치
        return commonDAO.selectOne("recruitDAO.selectListTotCnt", vo);
    }

    /** 내가 연 모집(마이페이지) — 본인 reg_id 기준. */
    public List<RecruitVO> selectMyList() {
        RecruitVO vo = new RecruitVO();
        vo.setRegId(SecurityUtil.getCurrentUserId());
        return commonDAO.selectList("recruitDAO.selectListMine", vo);
    }

    /** 상세. 조회수는 viewUp='Y'(프론트 dedup 통과)일 때만 증가 — 새로고침·내부조회 중복증가 방지. */
    public RecruitVO selectView(RecruitVO vo) {
        vo.setViewerId(viewerId()); // mine_yn(내가 연 모집) 판정용
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", vo);
        if (recruit == null) {
            return null;
        }
        if ("Y".equals(vo.getViewUp())) {
            commonDAO.update("recruitDAO.updateViewCnt", vo);
        }
        recruit.setRegId(null); // 주최자 로그인 ID는 응답에서 제외(공개 API — handle/mineYn으로 대체)
        return recruit;
    }

    /** 등록(로그인 회원). 주최자=reg_id(AuditInterceptor), 상태=모집중 기본. */
    public void insert(RecruitVO vo) {
        commonDAO.insert("recruitDAO.insert", vo);
    }

    /** 수정 — 주최자·관리자만. */
    public void update(RecruitVO vo) {
        assertOwner(vo.getDbKey());
        commonDAO.update("recruitDAO.update", vo);
    }

    /** 모집 상태 변경(마감/재개) — 주최자·관리자만. */
    public void updateStatus(RecruitVO vo) {
        assertOwner(vo.getDbKey());
        commonDAO.update("recruitDAO.updateStatus", vo);
    }

    /** 삭제(논리) + 딸린 신청 일괄 비활성 — 주최자·관리자만. */
    @Transactional
    public void delete(RecruitVO vo) {
        assertOwner(vo.getDbKey());
        commonDAO.delete("recruitDAO.delete", vo);
        RecruitApplyVO applyParam = new RecruitApplyVO();
        applyParam.setRecruitId(vo.getDbKey());
        commonDAO.update("recruitDAO.deleteApplyByRecruit", applyParam);
    }

    // ===== 참여 신청 =====
    /** 특정 모집의 신청자 목록 — 주최자·관리자만. */
    public List<RecruitApplyVO> selectApplyList(RecruitApplyVO vo) {
        assertOwner(vo.getRecruitId());
        return commonDAO.selectList("recruitDAO.selectApplyList", vo);
    }

    /** 내 신청 내역(로그인 본인). */
    public List<RecruitApplyVO> selectApplyListMine(RecruitApplyVO vo) {
        vo.setUserId(currentUserId());
        return commonDAO.selectList("recruitDAO.selectApplyListMine", vo);
    }

    /**
     * 참여 신청(로그인 회원). 본인 모집·중복 신청 차단. 상태는 항상 대기(APPLY01) 강제.
     * 마감 처리는 두 경우를 구분한다 — 정원이 차서 자동 마감된 건은 <b>대기 신청</b>을 받고
     * (자리가 나면 주최자가 대기자를 수락), 주최자가 정원 미달에서 직접 닫은 건은 더 받지 않는다.
     */
    @Transactional
    public void applyInsert(RecruitApplyVO vo) {
        String me = currentUserId();
        RecruitVO key = new RecruitVO();
        key.setDbKey(vo.getRecruitId());
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        if (recruit == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "모집을 찾을 수 없습니다.");
        }
        if ("RECRUIT02".equals(recruit.getStatusCd()) && !isFullByCapacity(recruit)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "마감된 모집입니다.");
        }
        if (isPastMeetDt(recruit)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 지난 모임입니다.");
        }
        if (me.equals(recruit.getRegId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인이 등록한 모집에는 신청할 수 없습니다.");
        }
        Integer dup = commonDAO.selectOne("recruitDAO.selectApplyCountByUser", applyKey(vo.getRecruitId(), me));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 신청한 모집입니다.");
        }
        vo.setUserId(me);
        vo.setApplyStatus("APPLY01"); // 신청은 항상 대기로 생성 — 수락(APPLY02)은 주최자만(applyUpdate). 클라이언트 위조 차단.
        commonDAO.insert("recruitDAO.insertApply", vo);
        notificationService.notify(recruit.getRegId(), "APPLY",
                "'" + recruit.getTitle() + "' 모집에 새 참여 신청이 도착했어요.",
                "/gen/recruit/" + vo.getRecruitId());
    }

    /** 신청 수락/거절 — 대상 모집 주최자·관리자만. 수락 시 정원 초과 차단 + 정원 충족 시 자동 마감(capacity>0일 때만). */
    @Transactional
    public void applyUpdate(RecruitApplyVO vo) {
        RecruitApplyVO apply = commonDAO.selectOne("recruitDAO.selectApplyView", vo);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "신청을 찾을 수 없습니다.");
        }
        assertOwner(apply.getRecruitId());
        RecruitVO key = new RecruitVO();
        key.setDbKey(apply.getRecruitId());
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        boolean accept = "APPLY02".equals(vo.getApplyStatus());

        if (accept) {
            int cap = parseCnt(recruit.getCapacity());
            int accepted = parseCnt(recruit.getAcceptedCnt());
            if (cap > 0 && accepted >= cap) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "정원이 가득 찼습니다. 모집을 마감해 주세요.");
            }
            commonDAO.update("recruitDAO.updateApplyStatus", vo);
            if (cap > 0 && accepted + 1 >= cap) { // 정원 충족 → 자동 마감
                RecruitVO close = new RecruitVO();
                close.setDbKey(apply.getRecruitId());
                close.setStatusCd("RECRUIT02");
                commonDAO.update("recruitDAO.updateStatus", close);
            }
        } else {
            commonDAO.update("recruitDAO.updateApplyStatus", vo);
        }
        // 신청자에게 결과 알림
        notificationService.notify(apply.getUserId(), accept ? "ACCEPT" : "REJECT",
                "'" + recruit.getTitle() + "' 모집 참여가 " + (accept ? "수락되었어요! 🎉" : "아쉽게 거절되었어요."),
                "/gen/recruit/" + apply.getRecruitId());
    }

    /**
     * 참석 결과 기록(주최자·관리자) — 모임이 종료된 뒤, 수락된 참여자에게만.
     * 노쇼는 프로필 신뢰지표에 노출되므로 함부로 기록되지 않도록 조건을 서버에서 강제한다.
     */
    @Transactional
    public void applyAttend(RecruitApplyVO vo) {
        RecruitApplyVO apply = commonDAO.selectOne("recruitDAO.selectApplyView", vo);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "신청을 찾을 수 없습니다.");
        }
        assertOwner(apply.getRecruitId()); // 주최자·관리자만
        if (!"APPLY02".equals(apply.getApplyStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수락된 참여자만 참석 결과를 기록할 수 있습니다.");
        }
        String cd = vo.getAttendCd();
        boolean clear = cd == null || cd.isBlank();
        if (!clear && !"ATTEND01".equals(cd) && !"ATTEND02".equals(cd) && !"ATTEND03".equals(cd)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 참석 결과입니다.");
        }
        RecruitVO key = new RecruitVO();
        key.setDbKey(apply.getRecruitId());
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        if (recruit != null && !isFinished(recruit)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "모임이 끝난 뒤에 기록할 수 있습니다. (마감 또는 모임일 경과)");
        }
        RecruitApplyVO upd = new RecruitApplyVO();
        upd.setDbKey(vo.getDbKey());
        upd.setAttendCd(clear ? "" : cd);
        commonDAO.update("recruitDAO.updateApplyAttend", upd);
    }

    /**
     * 정원이 차서 닫힌 상태인지(정원 설정 있고 수락 수가 정원 이상).
     * 같은 RECRUIT02라도 '정원 충족 자동 마감'과 '주최자 수동 마감'을 이 기준으로 구분한다
     * (전자만 대기 신청을 받는다). 별도 플래그 컬럼 없이 정원·수락수로 판정한다.
     */
    private boolean isFullByCapacity(RecruitVO recruit) {
        int cap = parseCnt(recruit.getCapacity());
        return cap > 0 && parseCnt(recruit.getAcceptedCnt()) >= cap;
    }

    /** 모임 종료 판정 — 마감(RECRUIT02) 또는 모임일이 오늘보다 이전. (후기 자격과 동일 기준) */
    private boolean isFinished(RecruitVO recruit) {
        return "RECRUIT02".equals(recruit.getStatusCd()) || isPastMeetDt(recruit);
    }

    /**
     * 모임일이 이미 지났는지(일정 미지정이면 false).
     * 마감 여부와 분리해서 본다 — 정원 충족으로 마감된 모집에는 대기 신청을 받아야 하므로
     * '마감=끝'으로 묶으면 대기 명단이 동작하지 않는다.
     */
    private boolean isPastMeetDt(RecruitVO recruit) {
        String meetDt = recruit.getMeetDt();
        if (meetDt == null || meetDt.isBlank()) {
            return false;
        }
        return meetDt.compareTo(java.time.LocalDate.now().toString()) < 0;
    }

    /** 회원 참석 통계(참석/불참/노쇼) — 공개 프로필 신뢰지표. */
    public java.util.Map<String, Object> selectAttendStats(String userId) {
        java.util.Map<String, Object> p = new java.util.HashMap<>();
        p.put("userId", userId);
        return commonDAO.selectOne("recruitDAO.selectAttendStats", p);
    }

    /** 문자열 수치를 int로(널·공백·비수치는 0). */
    private int parseCnt(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 신청 취소 — 신청자 본인·관리자만. */
    public void applyDelete(RecruitApplyVO vo) {
        RecruitApplyVO apply = commonDAO.selectOne("recruitDAO.selectApplyView", vo);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "신청을 찾을 수 없습니다.");
        }
        SecurityUtil.assertOwnerOrAdmin(apply.getUserId());
        commonDAO.delete("recruitDAO.deleteApply", vo);
    }

    // ===== 공통 인가 =====
    /** 모집 주최자 또는 관리자만 통과. 없으면 예외. */
    private void assertOwner(String recruitId) {
        RecruitVO key = new RecruitVO();
        key.setDbKey(recruitId);
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        if (recruit == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "모집을 찾을 수 없습니다.");
        }
        SecurityUtil.assertOwnerOrAdmin(recruit.getRegId());
    }

    private String currentUserId() {
        String me = SecurityUtil.getCurrentUserId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }

    private RecruitApplyVO applyKey(String recruitId, String userId) {
        RecruitApplyVO v = new RecruitApplyVO();
        v.setRecruitId(recruitId);
        v.setUserId(userId);
        return v;
    }

    // ===== 모임 리마인더(스케줄 배치) =====

    /** 매일 지정 시각에 '내일 모임' 알림 발송(cron 설정 가능). */
    @Scheduled(cron = "${recruit.remind.cron:0 0 9 * * *}")
    public void scheduledMeetReminder() {
        sendMeetReminders(java.time.LocalDate.now().plusDays(1).toString());
    }

    /**
     * 지정일에 모임이 있는 건의 주최자·수락 참여자에게 리마인더 알림 적재.
     * 알림 1건씩 독립 트랜잭션(NotificationService.notify)이라 일부 실패가 나머지를 막지 않는다.
     * 같은 날 재실행하면 매퍼의 NOT EXISTS가 이미 보낸 수신자를 걸러 중복 발송되지 않는다.
     *
     * @param targetDt 모임일(YYYY-MM-DD)
     * @return 발송 건수(수신자 단위)
     */
    public int sendMeetReminders(String targetDt) {
        Map<String, Object> param = new java.util.HashMap<>();
        param.put("targetDt", targetDt);
        List<Map<String, Object>> targets = commonDAO.selectList("recruitDAO.selectRemindTargets", param);
        int sent = 0;
        for (Map<String, Object> t : targets) {
            String userId = str(t.get("userId"));
            String recruitId = str(t.get("recruitId"));
            String title = str(t.get("title"));
            if (userId == null || recruitId == null) {
                continue;
            }
            notificationService.notify(userId, "REMIND",
                    "'" + title + "' 모임이 내일이에요. (" + str(t.get("meetDt")) + ")",
                    "/gen/recruit/" + recruitId);
            sent++;
        }
        if (sent > 0) {
            log.info("[RecruitRemind] {} 모임 리마인더 {}건 발송", targetDt, sent);
        }
        return sent;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
