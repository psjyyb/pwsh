package com.pwsh.domain.member.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.common.event.SessionEndReason;
import com.pwsh.common.event.SessionInvalidatedEvent;
import com.pwsh.domain.eventlog.service.EventLogService;
import com.pwsh.domain.mail.service.MailService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 업무 로직. 컨트롤러는 매핑·입력검증만, 로직(ID중복·BCrypt·매핑 저장)은 여기(단일 @Service).
 * 비밀번호 복잡도 검증(PasswordPolicy)은 컨트롤러 진입부(인코딩 전 원문 검사).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final CommonDAO commonDAO;
    private final PasswordEncoder passwordEncoder;
    private final EventLogService eventLogService;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;

    /** 휴면 안내 메일의 로그인 링크. 사이트 주소는 배포 환경마다 달라 설정으로 받는다 */
    @Value("${site.login-url:/}")
    private String loginUrl;

    /**
     * 발급된 토큰 전부 무효화 — <b>토큰을 죽이는 유일한 창구</b>.
     *
     * <p>token_ver를 올리는 것만으로는 부족하다. 접속 세션도 닫아야 하는데, 그 짝을 호출부마다
     * 손으로 맞추면 언젠가 한 곳을 빠뜨려 "토큰은 죽었는데 접속 현황에는 접속중으로 남는" 상태가
     * 된다(컴파일러가 못 잡는다). 이 프로젝트는 토큰을 죽이는 진입점이 8곳이라 특히 위험하다 —
     * 로그아웃·본인 비번변경·비밀번호 재설정·탈퇴·관리자 리셋·강제로그아웃·계정정지.
     * 그래서 여기서만 무효화하고 이벤트를 발행해, 뒤처리는 리스너가 책임지게 한다.
     *
     * <p>로그인은 예외다 — 새 token_ver 값을 받아 토큰을 발급해야 하므로 인증 서비스가 직접 올리고
     * {@code LoginSucceededEvent}를 발행한다.
     *
     * @param reason {@link SessionEndReason} 값(세션 종료 사유로 그대로 기록된다)
     */
    public void invalidateToken(String memberId, String reason) {
        MemberVO param = new MemberVO();
        param.setMemberId(memberId);
        commonDAO.selectOne("memberDAO.incrementTokenVer", param);
        eventPublisher.publishEvent(new SessionInvalidatedEvent(memberId, reason));
    }

    public List<MemberVO> selectList(MemberVO vo) {
        return commonDAO.selectList("memberDAO.selectList", vo);
    }

    public int selectListTotalCount(MemberVO vo) {
        return commonDAO.selectOne("memberDAO.selectListTotalCount", vo);
    }

    public MemberVO selectView(MemberVO vo) {
        return commonDAO.selectOne("memberDAO.selectView", vo);
    }

    /** 등록: ID 중복검사 + 비번 BCrypt 인코딩 */
    public void insert(MemberVO vo) {
        Integer cnt = commonDAO.selectOne("memberDAO.selectCount", vo);
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.member.duplicateId"));
        }
        vo.setPassword(passwordEncoder.encode(vo.getPassword()));
        commonDAO.insert("memberDAO.insert", vo);
    }

    /** 비밀번호 변경(BCrypt) — 관리자 리셋. 대상 사용자의 token_ver를 올려 기존 세션 무효화. */
    @Transactional
    public void updatePassword(MemberVO vo) {
        vo.setPassword(passwordEncoder.encode(vo.getPassword()));
        commonDAO.update("memberDAO.updatePw", vo);
        invalidateToken(vo.getRowId(), SessionEndReason.PWCHANGE); // updatePw는 rowId(=member_id) 기준
    }

    /** 정보 수정(비번 제외) */
    public void updateInfo(MemberVO vo) {
        commonDAO.update("memberDAO.updateInfo", vo);
    }

    /** 삭제(논리, 탈퇴) — 사용자의 권한그룹 매핑(auth_member)도 정리(고아 방지) */
    @Transactional
    public void delete(MemberVO vo) {
        vo.setMemberId(vo.getRowId()); // 매핑 삭제는 memberId 기준
        commonDAO.delete("memberDAO.deleteAuthMember", vo);
        commonDAO.delete("memberDAO.delete", vo);
    }

    /** 사용자의 권한그룹 ID 목록 */
    public List<String> selectAuthGroupIds(MemberVO vo) {
        return commonDAO.selectList("memberDAO.selectAuthGroupIds", vo);
    }

    /** 관리자 강제 로그아웃 — 대상 사용자의 token_ver +1로 발급된 토큰(access·refresh) 즉시 무효화. */
    public void forceLogout(MemberVO vo) {
        invalidateToken(vo.getMemberId(), SessionEndReason.FORCE);
        eventLogService.write("MEMBER_LOGOUT", "member", vo.getMemberId());
    }

    /**
     * 관리자 제재(계정 상태 변경) — STATUS03(정지) / STATUS01(정상 해제).
     * 정지 시 token_ver를 올려 이미 발급된 access 토큰까지 즉시 무효화한다
     * (JWT 필터는 상태가 아니라 token_ver로 판정하므로, 올리지 않으면 만료 전까지 계속 접근 가능).
     */
    @Transactional
    public void updateStatus(MemberVO vo) {
        String status = vo.getStatusCd();
        if (!"STATUS01".equals(status) && !"STATUS03".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.member.invalidStatus"));
        }
        commonDAO.update("memberDAO.updateStatus", vo);
        if ("STATUS03".equals(status)) {
            invalidateToken(vo.getMemberId(), SessionEndReason.SUSPEND); // 정지 즉시 접근 차단
        }
        // 감사: 컨트롤러 진입점이 updateStatus라 AOP 대상이 아니므로 여기서 직접 남긴다(제재는 추적 필수).
        eventLogService.write("STATUS03".equals(status) ? "MEMBER_SUSPEND" : "MEMBER_RESTORE", "member", vo.getMemberId());
    }

    /** 사용자-권한그룹 매핑 저장 — 기존 삭제 후 재등록 */
    @Transactional
    public void saveAuthGroup(MemberVO vo) {
        commonDAO.delete("memberDAO.deleteAuthMember", vo);
        if (vo.getAuthGroupIds() != null) {
            for (String authGroupId : vo.getAuthGroupIds()) {
                vo.setAuthGroupId(authGroupId);
                commonDAO.insert("memberDAO.insertAuthMember", vo);
            }
        }
        eventLogService.write("MEMBER_AUTH_GROUP", "member", vo.getMemberId());
    }

    // ===== 회원 라이프사이클 (휴면 · 파기) =====

    /**
     * 장기 미접속 계정을 휴면으로 전환한다. 전환 즉시 발급된 토큰을 무효화하고 접속 세션도 닫는다 —
     * 안 그러면 "휴면인데 아직 로그인된 채로 돌아다니는" 계정이 남는다.
     *
     * @return 전환 건수
     */
    @Transactional
    public int sweepDormant() {
        List<MemberVO> targets = commonDAO.selectList("memberDAO.selectDormantTargets", new MemberVO());
        int changed = 0;
        for (MemberVO target : targets) {
            MemberVO p = new MemberVO();
            p.setMemberId(target.getMemberId());
            if (commonDAO.update("memberDAO.updateDormant", p) == 0) {
                continue; // 그 사이 상태가 바뀐 계정(로그인 등) — 건너뛴다
            }
            invalidateToken(target.getMemberId(), SessionEndReason.DORMANT);
            eventLogService.write("MEMBER_DORMANT", "member", target.getMemberId());
            changed++;
        }
        return changed;
    }

    /**
     * 휴면 전환 예정 안내 메일. 발송 실패해도 계속 진행한다 —
     * 한 명의 주소가 잘못돼 배치가 멈추면 나머지가 통지 없이 휴면이 된다(실패는 mail_log에 남는다).
     *
     * @return 발송 시도 건수
     */
    public int notifyDormantSoon() {
        List<MemberVO> targets = commonDAO.selectList("memberDAO.selectDormantNotifyTargets", new MemberVO());
        String siteTitle = configTitle();
        for (MemberVO target : targets) {
            if (target.getEmail() == null || target.getEmail().isBlank()) {
                continue; // 보낼 주소가 없으면 통지할 방법이 없다
            }
            mailService.send("DORMANT_NOTICE", target.getEmail(), Map.of(
                    "siteTitle", siteTitle,
                    "memberName", displayName(target),
                    "dormantDt", target.getDormantDt() == null ? "" : target.getDormantDt(),
                    "loginUrl", loginUrl));
        }
        return targets.size();
    }

    /** 휴면 해제(관리자). 마지막 접속을 지금으로 당겨 다음 배치에서 곧바로 다시 휴면이 되지 않게 한다. */
    @Transactional
    public void restoreDormant(MemberVO vo) {
        if (commonDAO.update("memberDAO.updateRestore", vo) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "휴면 상태인 계정만 해제할 수 있습니다.");
        }
        // 정지 해제와 같은 "정상으로 되돌린다" 행위라 코드를 공유한다(code MEMBER_RESTORE)
        eventLogService.write("MEMBER_RESTORE", "member", vo.getMemberId());
    }

    /**
     * 탈퇴 후 보존기간이 지난 계정의 <b>개인정보 컬럼만</b> 비운다.
     *
     * <p>행은 남긴다 — {@code post.reg_id}·{@code comment.reg_id}·{@code recruit.reg_id}가 회원 ID로
     * 붙어 있어 행을 지우면 과거 게시글·모집의 작성자가 깨진다. 파기 사실은 event_log에 남는다.
     *
     * @return 파기 건수
     */
    @Transactional
    public int sweepDestroy() {
        List<MemberVO> targets = commonDAO.selectList("memberDAO.selectDestroyTargets", new MemberVO());
        int changed = 0;
        for (MemberVO target : targets) {
            MemberVO p = new MemberVO();
            p.setMemberId(target.getMemberId());
            if (commonDAO.update("memberDAO.updateDestroy", p) == 0) {
                continue;
            }
            eventLogService.write("MEMBER_DESTROY", "member", target.getMemberId());
            changed++;
        }
        return changed;
    }

    /** 즉시 파기(관리자) — 보존기간을 기다리지 않고 지금 비운다(정보주체 요청 등). */
    @Transactional
    public void destroyNow(MemberVO vo) {
        if (commonDAO.update("memberDAO.updateDestroy", vo) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 파기되었거나 없는 계정입니다.");
        }
        // 계정이 use_yn='N'으로 내려가므로 살아 있던 세션도 같이 끊는다
        invalidateToken(vo.getMemberId(), SessionEndReason.WITHDRAW);
        eventLogService.write("MEMBER_DESTROY", "member", vo.getMemberId());
    }

    /** 매일 새벽 — 안내 먼저, 그다음 전환·파기. 순서가 바뀌면 통지 없이 휴면이 되는 계정이 생긴다. */
    @Scheduled(cron = "${member.lifecycle.cron:0 0 3 * * *}")
    public void scheduledLifecycle() {
        int notified = notifyDormantSoon();
        int dormant = sweepDormant();
        int destroyed = sweepDestroy();
        if (notified + dormant + destroyed > 0) {
            log.info("회원 라이프사이클: 휴면예정 안내 {}건, 휴면 전환 {}건, 개인정보 파기 {}건",
                    notified, dormant, destroyed);
        }
    }

    /** 안내메일 수신자 표기 — 이 서비스의 표시명은 닉네임이다(이름은 선택 입력이라 비어 있을 수 있다). */
    private String displayName(MemberVO vo) {
        if (vo.getNickname() != null && !vo.getNickname().isBlank()) {
            return vo.getNickname();
        }
        return vo.getMemberName() == null || vo.getMemberName().isBlank() ? vo.getMemberId() : vo.getMemberName();
    }

    private String configTitle() {
        String title = commonDAO.selectOne("configDAO.selectTitle", new MemberVO());
        return title == null ? "" : title;
    }
}
