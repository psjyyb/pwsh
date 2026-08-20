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
    private final com.pwsh.domain.block.service.BlockService blockService;
    private final com.pwsh.global.realtime.RealtimeService realtimeService;
    private final com.pwsh.domain.follow.service.FollowService followService;

    // ===== 모집 =====
    public List<RecruitVO> selectList(RecruitVO vo) {
        vo.setViewerId(viewerId()); // mine_yn(내가 연 모집) 판정용
        return commonDAO.selectList("recruitDAO.selectList", vo);
    }

    /** mine_yn 판정용 현재 조회자 — 비로그인/시스템은 null. */
    private String viewerId() {
        String me = SecurityUtil.getCurrentMemberId();
        return (me == null || "system".equals(me)) ? null : me;
    }

    public int selectListTotalCount(RecruitVO vo) {
        vo.setViewerId(viewerId()); // 목록과 동일한 차단 필터를 적용해 총건수 일치
        return commonDAO.selectOne("recruitDAO.selectListTotalCount", vo);
    }

    /** 내가 연 모집(마이페이지) — 본인 reg_id 기준. */
    public List<RecruitVO> selectMyList() {
        RecruitVO vo = new RecruitVO();
        vo.setRegId(SecurityUtil.getCurrentMemberId());
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

    /**
     * 등록(로그인 회원). 주최자=reg_id(AuditInterceptor), 상태=모집중 기본.
     * 등록 후 관심 회원에게 알림 — <b>나를 팔로우한 사람 먼저</b>, 그다음 그 취미를 담은 회원(중복 제외).
     */
    public void insert(RecruitVO vo) {
        validatePlace(vo);
        commonDAO.insert("recruitDAO.insert", vo);
        java.util.Set<String> notified = notifyMyFollowers(vo);
        notifyHobbyFollowers(vo, notified);
    }

    /**
     * 나를 팔로우한 회원에게 '팔로우한 사람의 새 모집' 알림.
     * 반환값(발송한 회원)은 취미 알림과 중복되지 않도록 호출자가 제외 목록으로 넘긴다.
     */
    private java.util.Set<String> notifyMyFollowers(RecruitVO vo) {
        String host = SecurityUtil.getCurrentMemberId();
        java.util.Set<String> sent = new java.util.LinkedHashSet<>();
        if (host == null) {
            return sent;
        }
        List<String> followers = followService.selectFollowerIds(host);
        if (followers.isEmpty()) {
            return sent;
        }
        RecruitVO key = new RecruitVO();
        key.setRowId(vo.getRowId());
        RecruitVO saved = commonDAO.selectOne("recruitDAO.selectView", key);
        String hostName = saved != null && saved.getRegName() != null ? saved.getRegName() : "팔로우한 회원";
        String title = saved != null && saved.getTitle() != null ? saved.getTitle() : "새 모집";
        String link = "/gen/recruit/" + vo.getRowId();
        for (String uid : followers) {
            if (blockService.isBlockedBy(host, uid)) {
                continue; // 주최자를 차단한 회원에게는 보내지 않는다
            }
            notificationService.notify(uid, "NEWRECRUIT", hostName + "님이 새 모집을 열었어요: " + title, link);
            sent.add(uid);
        }
        if (!sent.isEmpty()) {
            log.info("[NewRecruit] 팔로워 {}명에게 알림", sent.size());
        }
        return sent;
    }

    /**
     * 그 취미를 담은 회원에게 '새 모집' 알림 — 관심 있는 사람만 받는다(member_hobby 기준).
     * 주최자를 차단한 회원은 제외한다. 알림 1건씩 독립 트랜잭션이라 실패가 모집 등록을 되돌리지 않는다.
     *
     * <p>담은 회원 수만큼 순차 발행하므로 인기 취미에서는 등록 응답이 길어질 수 있다.
     * 현재 규모에서는 문제되지 않지만, 회원이 크게 늘면 비동기(@Async)나 배치로 옮겨야 한다.
     */
    private void notifyHobbyFollowers(RecruitVO vo) {
        notifyHobbyFollowers(vo, java.util.Set.of());
    }

    /** @param already 이미 다른 사유로 알림을 보낸 회원(중복 발송 방지 — 예: 이전 회차 참여자) */
    private void notifyHobbyFollowers(RecruitVO vo, java.util.Set<String> already) {
        String host = SecurityUtil.getCurrentMemberId();
        if (vo.getHobbyId() == null || vo.getHobbyId().isBlank() || host == null) {
            return;
        }
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("hobbyId", vo.getHobbyId());
        p.put("hostId", host);
        List<String> followers = commonDAO.selectList("recruitDAO.selectHobbyFollowers", p);
        if (followers.isEmpty()) {
            return;
        }
        RecruitVO key = new RecruitVO();
        key.setRowId(vo.getRowId());
        RecruitVO saved = commonDAO.selectOne("recruitDAO.selectView", key);
        String hobbyName = saved != null && saved.getHobbyName() != null ? saved.getHobbyName() : "관심 취미";
        String title = saved != null && saved.getTitle() != null ? saved.getTitle() : "새 모집";
        String link = "/gen/recruit/" + vo.getRowId();
        for (String uid : followers) {
            if (already.contains(uid) || blockService.isBlockedBy(host, uid)) {
                continue; // 이미 받은 회원(이전 회차 참여자)·주최자를 차단한 회원에게는 보내지 않는다
            }
            notificationService.notify(uid, "NEWRECRUIT",
                    "[" + hobbyName + "] 새 모집이 열렸어요: " + title, link);
        }
        log.info("[NewRecruit] hobby={} 대상 {}명에게 알림", vo.getHobbyId(), followers.size());
    }

    /**
     * 다음 회차 만들기(정기 모임) — 기존 모집을 복제해 일정만 바꿔 새로 연다. 주최자·관리자만.
     *
     * <p>회차를 잇는 컬럼(원본 id·회차 번호)은 두지 않는다. 참여자·대화·조회수를 물려받지 않는
     * <b>독립된 새 모집</b>이라 연결을 남기면 "이전 회차 신청자는 자동 참여인가?" 같은 모호한 상태가
     * 생긴다. 대신 이전 회차 확정 참여자에게 알림을 보내 다시 신청할 수 있게 한다.
     *
     * <p>vo에 값이 있으면 그 값으로, 없으면 원본 값을 그대로 쓴다(제목·설명·정원·지역).
     * 일정(meetDt)은 회차를 구분하는 유일한 값이라 필수이고, 지난 날짜는 받지 않는다.
     */
    @Transactional
    public void copy(RecruitVO vo) {
        String srcId = vo.getRowId();
        assertOwner(srcId);
        RecruitVO key = new RecruitVO();
        key.setRowId(srcId);
        RecruitVO src = commonDAO.selectOne("recruitDAO.selectView", key);
        if (src == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "모집을 찾을 수 없습니다.");
        }
        String meetDt = vo.getMeetDt() == null ? "" : vo.getMeetDt().trim();
        if (meetDt.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "다음 모임 일정을 선택해 주세요.");
        }
        if (meetDt.compareTo(java.time.LocalDate.now().toString()) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지난 날짜로는 다음 회차를 만들 수 없습니다.");
        }

        RecruitVO next = new RecruitVO();
        next.setHobbyId(src.getHobbyId());
        next.setTitle(pick(vo.getTitle(), src.getTitle()));
        next.setContent(pick(vo.getContent(), src.getContent()));
        next.setCapacity(pick(vo.getCapacity(), src.getCapacity()));
        next.setAreaCd(pick(vo.getAreaCd(), src.getAreaCd()));
        next.setRegion(pick(vo.getRegion(), src.getRegion()));
        // 정기 모임은 같은 곳에서 다시 모이는 게 보통이라 장소·좌표도 물려받는다(일정만 바뀜)
        next.setPlaceName(pick(vo.getPlaceName(), src.getPlaceName()));
        next.setAddr(pick(vo.getAddr(), src.getAddr()));
        next.setLat(pick(vo.getLat(), src.getLat()));
        next.setLng(pick(vo.getLng(), src.getLng()));
        next.setMeetDt(meetDt); // 상태는 매퍼 기본값(RECRUIT01 모집중) — 복제본은 항상 새로 모집한다
        commonDAO.insert("recruitDAO.insert", next);
        vo.setRowId(next.getRowId()); // 컨트롤러가 새 모집 ID를 반환

        java.util.Set<String> notified = notifyPrevMembers(srcId, next);
        notifyHobbyFollowers(next, notified);
    }

    /**
     * 이전 회차 확정 참여자에게 '다음 회차' 알림. 반환값은 발송한 회원 — 취미 팔로워 알림과 중복되지 않게
     * 호출자가 제외 목록으로 넘긴다(같은 사람이 알림 2건을 받으면 스팸처럼 보인다).
     */
    private java.util.Set<String> notifyPrevMembers(String srcId, RecruitVO next) {
        String host = SecurityUtil.getCurrentMemberId();
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("recruitId", srcId);
        p.put("memberId", host);
        java.util.Set<String> sent = new java.util.LinkedHashSet<>();
        String link = "/gen/recruit/" + next.getRowId();
        for (String uid : commonDAO.<String>selectList("recruitDAO.selectAcceptedMembers", p)) {
            if (blockService.isBlockedBy(host, uid)) {
                continue;
            }
            notificationService.notify(uid, "NEWRECRUIT",
                    "'" + next.getTitle() + "' 다음 모임이 열렸어요. (" + next.getMeetDt() + ")", link);
            sent.add(uid);
        }
        if (!sent.isEmpty()) {
            log.info("[RecruitCopy] 모집 {} → {} 이전 회차 참여자 {}명에게 알림", srcId, next.getRowId(), sent.size());
        }
        return sent;
    }

    /** 입력값이 있으면 그것을, 없으면 원본값을 쓴다(복제 시 부분 수정 허용). */
    private String pick(String input, String fallback) {
        return input == null || input.isBlank() ? fallback : input.trim();
    }

    /** 수정 — 주최자·관리자만. */
    public void update(RecruitVO vo) {
        assertOwner(vo.getRowId());
        validatePlace(vo);
        commonDAO.update("recruitDAO.update", vo);
    }

    /**
     * 장소(지도) 입력 검증. 좌표는 클라이언트가 보내는 값이라 그대로 믿지 않는다.
     * - 숫자가 아니면 400(매퍼의 ::numeric 캐스트가 500으로 터지는 걸 막는다)
     * - 위도 ±90 / 경도 ±180 범위를 벗어나면 400
     * - 둘 중 하나만 오면 마커를 찍을 수 없으니 400
     * 장소를 아예 지정하지 않는 모집(온라인·미정)은 정상이라 빈 값은 통과시킨다.
     */
    private void validatePlace(RecruitVO vo) {
        boolean hasLat = vo.getLat() != null && !vo.getLat().isBlank();
        boolean hasLng = vo.getLng() != null && !vo.getLng().isBlank();
        if (!hasLat && !hasLng) {
            return;
        }
        if (hasLat != hasLng) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "장소 좌표가 올바르지 않습니다.");
        }
        double lat = parseCoord(vo.getLat());
        double lng = parseCoord(vo.getLng());
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "장소 좌표가 올바르지 않습니다.");
        }
    }

    private double parseCoord(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "장소 좌표가 올바르지 않습니다.");
        }
    }

    /** 모집 상태 변경(마감/재개) — 주최자·관리자만. */
    public void updateStatus(RecruitVO vo) {
        assertOwner(vo.getRowId());
        commonDAO.update("recruitDAO.updateStatus", vo);
    }

    /** 삭제(논리) + 딸린 신청 일괄 비활성 — 주최자·관리자만. */
    @Transactional
    public void delete(RecruitVO vo) {
        assertOwner(vo.getRowId());
        commonDAO.delete("recruitDAO.delete", vo);
        RecruitApplyVO applyParam = new RecruitApplyVO();
        applyParam.setRecruitId(vo.getRowId());
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
        vo.setMemberId(currentMemberId());
        return commonDAO.selectList("recruitDAO.selectApplyListMine", vo);
    }

    /**
     * 참여 신청(로그인 회원). 본인 모집·중복 신청 차단. 상태는 항상 대기(APPLY01) 강제.
     * 마감 처리는 두 경우를 구분한다 — 정원이 차서 자동 마감된 건은 <b>대기 신청</b>을 받고
     * (자리가 나면 주최자가 대기자를 수락), 주최자가 정원 미달에서 직접 닫은 건은 더 받지 않는다.
     */
    @Transactional
    public void applyInsert(RecruitApplyVO vo) {
        String me = currentMemberId();
        RecruitVO key = new RecruitVO();
        key.setRowId(vo.getRecruitId());
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
        Integer dup = commonDAO.selectOne("recruitDAO.selectApplyCountByMember", applyKey(vo.getRecruitId(), me));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 신청한 모집입니다.");
        }
        vo.setMemberId(me);
        vo.setApplyCd("APPLY01"); // 신청은 항상 대기로 생성 — 수락(APPLY02)은 주최자만(applyUpdate). 클라이언트 위조 차단.
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
        key.setRowId(apply.getRecruitId());
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        boolean accept = "APPLY02".equals(vo.getApplyCd());

        if (accept) {
            int cap = parseCnt(recruit.getCapacity());
            int accepted = parseCnt(recruit.getAcceptedCnt());
            if (cap > 0 && accepted >= cap) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "정원이 가득 찼습니다. 모집을 마감해 주세요.");
            }
            commonDAO.update("recruitDAO.updateApplyStatus", vo);
            if (cap > 0 && accepted + 1 >= cap) { // 정원 충족 → 자동 마감
                RecruitVO close = new RecruitVO();
                close.setRowId(apply.getRecruitId());
                close.setStatusCd("RECRUIT02");
                commonDAO.update("recruitDAO.updateStatus", close);
            }
        } else {
            commonDAO.update("recruitDAO.updateApplyStatus", vo);
        }
        // 신청자에게 결과 알림
        notificationService.notify(apply.getMemberId(), accept ? "ACCEPT" : "REJECT",
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
        if (!"APPLY02".equals(apply.getApplyCd())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수락된 참여자만 참석 결과를 기록할 수 있습니다.");
        }
        String cd = vo.getAttendCd();
        boolean clear = cd == null || cd.isBlank();
        if (!clear && !"ATTEND01".equals(cd) && !"ATTEND02".equals(cd) && !"ATTEND03".equals(cd)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 참석 결과입니다.");
        }
        RecruitVO key = new RecruitVO();
        key.setRowId(apply.getRecruitId());
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        if (recruit != null && !isFinished(recruit)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "모임이 끝난 뒤에 기록할 수 있습니다. (마감 또는 모임일 경과)");
        }
        RecruitApplyVO upd = new RecruitApplyVO();
        upd.setRowId(vo.getRowId());
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
    public java.util.Map<String, Object> selectAttendStats(String memberId) {
        java.util.Map<String, Object> p = new java.util.HashMap<>();
        p.put("memberId", memberId);
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
        SecurityUtil.assertOwnerOrAdmin(apply.getMemberId());
        commonDAO.delete("recruitDAO.deleteApply", vo);
    }

    // ===== 모임 단체 대화 =====

    /**
     * 모집별 단체 대화 목록 — 주최자 또는 수락(APPLY02)된 참여자만.
     * 멤버 판정을 매 요청 조인으로 하므로 수락이 취소되면 다음 요청부터 바로 막힌다.
     */
    public List<RecruitChatVO> selectChatList(RecruitChatVO vo) {
        String me = currentMemberId();
        assertChatMember(vo.getRecruitId(), me);
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("recruitId", vo.getRecruitId());
        p.put("viewerId", me);
        return commonDAO.selectList("recruitDAO.selectChatList", p);
    }

    /**
     * 대화 등록 — 멤버만. 저장 후 나를 제외한 멤버에게 SSE로 "새 글 있음"만 밀어준다
     * (본문은 목록 API로 다시 가져가므로 인가가 한 곳에 남는다).
     * 알림(notification)은 남기지 않는다 — 대화는 오가는 빈도가 높아 알림함이 잠긴다.
     */
    @Transactional
    public void chatInsert(RecruitChatVO vo) {
        String me = currentMemberId();
        assertChatMember(vo.getRecruitId(), me);
        String content = vo.getContent() == null ? "" : vo.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "내용을 입력해 주세요.");
        }
        if (content.length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1000자 이내로 입력해 주세요.");
        }
        vo.setContent(content);
        commonDAO.insert("recruitDAO.insertChat", vo);

        Map<String, Object> p = new java.util.HashMap<>();
        p.put("recruitId", vo.getRecruitId());
        p.put("memberId", me);
        List<String> members = commonDAO.selectList("recruitDAO.selectChatMembers", p);
        for (String uid : members) {
            realtimeService.push(uid, "recruitchat");
        }
        // 대화는 알림을 남기지 않지만(빈도가 높다), @닉네임으로 콕 집어 부른 경우는 예외로 알린다.
        // 대상은 그 대화를 볼 수 있는 멤버로 제한한다 — 밖에 있는 사람을 사적인 대화방으로 부르지 않는다.
        notificationService.notifyMentions(content, "/gen/recruit/" + vo.getRecruitId(),
                java.util.Set.of(), new java.util.HashSet<>(members));
    }

    /**
     * 대화 실시간 스트림 구독(SSE). 허브는 사용자 단위라 쪽지·알림 이벤트도 같은 연결로 오며,
     * 클라이언트가 이벤트 이름("recruitchat")으로 골라 쓴다.
     */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribeChat() {
        return realtimeService.subscribe(currentMemberId());
    }

    /** 대화 삭제(논리) — 작성자 본인 또는 관리자. */
    public void chatDelete(RecruitChatVO vo) {
        RecruitChatVO chat = commonDAO.selectOne("recruitDAO.selectChatView", vo);
        if (chat == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "대화를 찾을 수 없습니다.");
        }
        SecurityUtil.assertOwnerOrAdmin(chat.getRegId());
        commonDAO.update("recruitDAO.deleteChat", vo);
    }

    /** 내가 이 모집의 대화 참여 자격이 있는지(주최자·수락 참여자). 관리자도 자격이 없으면 못 본다(사적 대화). */
    private void assertChatMember(String recruitId, String memberId) {
        if (recruitId == null || recruitId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "모집을 선택해 주세요.");
        }
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("recruitId", recruitId);
        p.put("memberId", memberId);
        Integer cnt = commonDAO.selectOne("recruitDAO.selectChatMemberCnt", p);
        if (cnt == null || cnt == 0) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "참여가 확정된 회원만 대화할 수 있습니다.");
        }
    }

    // ===== 공통 인가 =====
    /** 모집 주최자 또는 관리자만 통과. 없으면 예외. */
    private void assertOwner(String recruitId) {
        RecruitVO key = new RecruitVO();
        key.setRowId(recruitId);
        RecruitVO recruit = commonDAO.selectOne("recruitDAO.selectView", key);
        if (recruit == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "모집을 찾을 수 없습니다.");
        }
        SecurityUtil.assertOwnerOrAdmin(recruit.getRegId());
    }

    private String currentMemberId() {
        String me = SecurityUtil.getCurrentMemberId();
        if (me == null || "system".equals(me)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return me;
    }

    private RecruitApplyVO applyKey(String recruitId, String memberId) {
        RecruitApplyVO v = new RecruitApplyVO();
        v.setRecruitId(recruitId);
        v.setMemberId(memberId);
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
            String memberId = str(t.get("memberId"));
            String recruitId = str(t.get("recruitId"));
            String title = str(t.get("title"));
            if (memberId == null || recruitId == null) {
                continue;
            }
            notificationService.notify(memberId, "REMIND",
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
