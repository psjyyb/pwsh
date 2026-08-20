package com.pwsh.domain.auth.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.eventlog.service.EventLogService;
import com.pwsh.domain.member.service.MemberVO;
import com.pwsh.global.security.CustomUserDetails;
import com.pwsh.global.security.SecurityUtil;
import com.pwsh.global.security.jwt.JwtTokenProvider;
import com.pwsh.global.web.ClientIpHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 업무 로직 — 로그인(계정잠금·실패횟수·JWT 발급)·토큰 재발급·비밀번호 변경/연장.
 * 컨트롤러는 매핑·입력검증(@Valid, PasswordPolicy)만. (단일 @Service)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final CommonDAO commonDAO;
    private final PasswordEncoder passwordEncoder;
    private final EventLogService eventLogService;
    private final EmailVerifyService emailVerifyService;

    public TokenResponse login(LoginRequest request) {
        // 계정 상태 사전 점검: 정지=차단, 잠금=시간 미경과면 차단 / 경과면 자동 해제
        MemberVO pre = commonDAO.selectOne("memberDAO.selectByMemberId", memberIdParam(request.memberId()));
        if (pre != null) {
            if ("STATUS03".equals(pre.getStatusCd())) {
                throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            }
            if ("STATUS02".equals(pre.getStatusCd())) {
                if ("Y".equals(pre.getLockActive())) {
                    throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                            "비밀번호를 " + pre.getFailCntLimit() + "회 이상 틀려 계정이 잠겼습니다. 약 "
                                    + pre.getLockRemainMin() + "분 후 다시 시도해 주세요.");
                }
                commonDAO.update("memberDAO.unlockAccount", memberIdParam(request.memberId())); // 잠금시간 경과 → 자동 해제
            }
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.memberId(), request.password()));
        } catch (BadCredentialsException e) {
            if (pre == null) {
                throw e; // 존재하지 않는 계정 → 일반 인증 실패
            }
            // 비밀번호 불일치 → 실패횟수 +1(제한 도달 시 자동 잠금), 상태 다시 조회해 안내
            commonDAO.update("memberDAO.increaseFailCnt", memberIdParam(request.memberId()));
            MemberVO after = commonDAO.selectOne("memberDAO.selectByMemberId", memberIdParam(request.memberId()));
            if (after == null) {
                throw e; // 재조회 사이 계정이 사라진 극단적 레이스 → 일반 인증 실패로 처리(NPE 방지)
            }
            if ("STATUS02".equals(after.getStatusCd())) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                        "비밀번호를 " + after.getFailCntLimit() + "회 틀려 계정이 " + after.getFailLockMins()
                                + "분간 잠겼습니다. " + after.getFailLockMins() + "분 후 다시 시도해 주세요.");
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "비밀번호가 일치하지 않습니다. (" + after.getFailCnt() + "/" + after.getFailCntLimit()
                            + "회) " + after.getFailCntLimit() + "회 틀리면 계정이 " + after.getFailLockMins() + "분간 잠깁니다.");
        }
        // 접속로그 audit(reg_id=로그인 사용자)을 위해 컨텍스트 설정 (STATELESS라 요청 종료 시 사라짐)
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        // 새 세션 시작: token_ver +1 → 다른 기기에 남아있던 토큰은 다음 요청에서 무효(단일세션 last-wins)
        String newVer = String.valueOf(
                (Integer) commonDAO.selectOne("memberDAO.incrementTokenVer", memberIdParam(userDetails.getMemberId())));
        String accessToken = jwtTokenProvider.createAccessToken(userDetails.getMemberId(), userDetails.getTypeCd(), newVer);
        String refreshToken = jwtTokenProvider.createRefreshToken(userDetails.getMemberId(), newVer);

        // 마지막 로그인 일시/IP 기록
        MemberVO loginInfo = new MemberVO();
        loginInfo.setMemberId(userDetails.getMemberId());
        loginInfo.setLastLoginIp(ClientIpHolder.get());
        commonDAO.update("memberDAO.updateLoginInfo", loginInfo);

        eventLogService.write("LOGIN", null, null);

        // 비밀번호 만료 알림(강제 아님)
        boolean pwExpired = "Y".equals(userDetails.getPwExpired());
        Integer pwDaysLeft = userDetails.getPwDaysLeft() != null
                ? Integer.valueOf(userDetails.getPwDaysLeft()) : null;
        return new TokenResponse(accessToken, refreshToken, pwExpired, pwDaysLeft);
    }

    /**
     * 셀프 회원가입 — 아이디·닉네임 중복검사 후 member 생성 + MEMBER 권한그룹 매핑(같은 트랜잭션).
     * 익명 요청이라 audit reg_id는 AuditInterceptor가 "system"으로 세팅한다.
     * (비밀번호 복잡도는 컨트롤러에서 PasswordPolicy로 선검증)
     */
    @Transactional
    public void signup(SignupRequest request) {
        if (!request.password().equals(request.pwConfirm())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호 확인이 일치하지 않습니다.");
        }
        // 이메일 인증코드 검증(발급받은 유효 코드여야 가입 진행)
        if (!emailVerifyService.verify(request.email(), "SIGNUP", request.code())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일 인증코드가 올바르지 않거나 만료되었습니다.");
        }
        // 아이디 중복
        MemberVO idCheck = new MemberVO();
        idCheck.setMemberId(request.memberId());
        Integer idCnt = commonDAO.selectOne("memberDAO.selectCount", idCheck);
        if (idCnt != null && idCnt > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 사용 중인 아이디입니다.");
        }
        // 닉네임 중복
        MemberVO nickCheck = new MemberVO();
        nickCheck.setNickname(request.nickname());
        Integer nickCnt = commonDAO.selectOne("memberDAO.selectCountByNickname", nickCheck);
        if (nickCnt != null && nickCnt > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 사용 중인 닉네임입니다.");
        }
        // 생성 (일반회원 MEM01, 정상 STATUS01, 비번 BCrypt. 실명은 미수집 → member_name null)
        MemberVO user = new MemberVO();
        user.setMemberId(request.memberId());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setTypeCd("MEM01");
        user.setNickname(request.nickname());
        user.setEmail(request.email()); // 선택(null 가능)
        user.setStatusCd("STATUS01");
        commonDAO.insert("memberDAO.insert", user);
        // MEMBER 권한그룹 매핑
        MemberVO authMember = new MemberVO();
        authMember.setMemberId(request.memberId());
        authMember.setAuthGroupId("MEMBER");
        commonDAO.insert("memberDAO.insertAuthMember", authMember);
        // 사용한 인증코드 소비(재사용 방지)
        emailVerifyService.consume(request.email(), "SIGNUP");
    }

    /** 가입 이메일 인증코드 발송(공개) — 형식만 확인하고 코드 발송. */
    public void sendSignupCode(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일을 입력해 주세요.");
        }
        emailVerifyService.issue(email.trim(), "SIGNUP", email.trim());
    }

    /**
     * 비밀번호 재설정 코드 발송(공개) — member_id로 회원 조회 → 등록된 이메일로 코드 발송.
     * 계정/이메일 존재 여부는 응답으로 노출하지 않는다(계정 열거 방지): 없으면 조용히 무시하고 성공처럼 응답.
     */
    public void sendResetCode(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return;
        }
        MemberVO u = commonDAO.selectOne("memberDAO.selectByMemberId", memberIdParam(memberId.trim()));
        if (u == null || !"Y".equals(u.getUseYn()) || u.getEmail() == null || u.getEmail().isBlank()) {
            return; // 열거 방지: 존재/미존재 구분 없이 동일 응답
        }
        emailVerifyService.issue(memberId.trim(), "RESET", u.getEmail());
    }

    /** 비밀번호 재설정(공개) — 인증코드 검증 후 새 비번 적용 + 세션 무효화(token_ver +1). */
    @Transactional
    public void resetPassword(PwResetRequest request) {
        if (!request.newPw().equals(request.pwConfirm())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호 확인이 일치하지 않습니다.");
        }
        if (!emailVerifyService.verify(request.memberId(), "RESET", request.code())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인증코드가 올바르지 않거나 만료되었습니다.");
        }
        MemberVO u = commonDAO.selectOne("memberDAO.selectByMemberId", memberIdParam(request.memberId()));
        if (u == null || !"Y".equals(u.getUseYn())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인증코드가 올바르지 않거나 만료되었습니다.");
        }
        MemberVO upd = new MemberVO();
        upd.setRowId(request.memberId());
        upd.setPassword(passwordEncoder.encode(request.newPw()));
        commonDAO.update("memberDAO.updatePw", upd);
        commonDAO.selectOne("memberDAO.incrementTokenVer", memberIdParam(request.memberId())); // 기존 세션 전부 무효화
        emailVerifyService.consume(request.memberId(), "RESET");
    }

    /** 내 정보(memberId·nickname·typeCd). 마이페이지 표시용. */
    public java.util.Map<String, String> me() {
        String memberId = SecurityUtil.getCurrentMemberId();
        MemberVO u = commonDAO.selectOne("memberDAO.selectByMemberId", memberIdParam(memberId));
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("memberId", memberId);
        m.put("nickname", u != null ? u.getNickname() : null);
        m.put("typeCd", u != null ? u.getTypeCd() : null);
        m.put("profileFileId", u != null ? u.getProfileFileId() : null);
        m.put("handle", u != null ? u.getHandle() : null); // 내 공개 식별자(본인 프로필 링크·소유자 비교용)
        return m;
    }

    /**
     * 회원 공개 프로필 — 조회 키는 공개 식별자(handle). 로그인 ID는 입력도 출력도 하지 않는다.
     * 닉네임·프로필사진 + 담은 취미 + 주최 모집 + 작성글(공개 취미게시판·비밀글 제외).
     * PII(이메일/이름/연락처)는 절대 노출하지 않는다. 비로그인도 조회 가능(공개).
     */
    public java.util.Map<String, Object> selectMemberProfile(String handle) {
        if (handle == null || handle.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회원을 찾을 수 없습니다.");
        }
        MemberVO h = new MemberVO();
        h.setHandle(handle);
        MemberVO u = commonDAO.selectOne("memberDAO.selectByHandle", h);
        if (u == null || !"Y".equals(u.getUseYn())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        String memberId = u.getMemberId(); // 내부 조회용(집계 쿼리 파라미터). 응답에는 넣지 않는다.
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("handle", u.getHandle());
        m.put("nickname", u.getNickname());
        m.put("profileFileId", u.getProfileFileId());
        // 담은 취미(공개)
        com.pwsh.domain.memberhobby.service.MemberHobbyVO hp = new com.pwsh.domain.memberhobby.service.MemberHobbyVO();
        hp.setMemberId(memberId);
        m.put("hobbies", commonDAO.selectList("memberHobbyDAO.selectMyList", hp));
        // 주최한 모집(전부 공개)
        com.pwsh.domain.recruit.service.RecruitVO rp = new com.pwsh.domain.recruit.service.RecruitVO();
        rp.setRegId(memberId);
        m.put("recruits", commonDAO.selectList("recruitDAO.selectListMine", rp));
        // 작성글(취미 공개게시판·비밀글 제외)
        com.pwsh.domain.post.service.PostVO bp = new com.pwsh.domain.post.service.PostVO();
        bp.setRegId(memberId);
        m.put("posts", commonDAO.selectList("postDAO.selectListByAuthor", bp));
        // 참석 통계(신뢰지표) — 기록된 모임만 집계. 노쇼는 프로필에 드러난다.
        java.util.Map<String, Object> ap = new java.util.HashMap<>();
        ap.put("memberId", memberId);
        m.put("attend", commonDAO.selectOne("recruitDAO.selectAttendStats", ap));
        return m;
    }

    /** 회원 탈퇴(셀프) — 현재 비번 확인 후 계정 비활성(use_yn='N') + 세션 무효화(재로그인·복구 불가). */
    @Transactional
    public void withdraw(String currentPw) {
        String memberId = SecurityUtil.getCurrentMemberId();
        if (memberId == null || "system".equals(memberId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        MemberVO user = commonDAO.selectOne("memberDAO.selectByMemberId", memberIdParam(memberId));
        if (user == null || !passwordEncoder.matches(currentPw, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "현재 비밀번호가 일치하지 않습니다.");
        }
        MemberVO upd = new MemberVO();
        upd.setRowId(memberId);
        commonDAO.update("memberDAO.delete", upd);            // use_yn='N' (로그인 차단)
        commonDAO.selectOne("memberDAO.incrementTokenVer", memberIdParam(memberId)); // 현재 토큰 즉시 무효화
    }

    /**
     * 본인 프로필 사진 파일 설정/해제 — member_id는 서버가 강제. fileId 없으면 해제(NULL).
     * 프로필로 지정한 파일은 공개 서빙되므로, 반드시 '본인이 업로드한 파일'만 허용(타인 비공개 이미지 id 지정 IDOR 차단).
     */
    @Transactional
    public void updateProfileImage(String fileId) {
        String memberId = SecurityUtil.getCurrentMemberId();
        if (memberId == null || "system".equals(memberId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        String normalized = (fileId == null || fileId.isBlank()) ? null : fileId;
        if (normalized != null) {
            java.util.Map<String, Object> p = new java.util.HashMap<>();
            p.put("fileId", normalized);
            p.put("memberId", memberId);
            Integer owned = commonDAO.selectOne("fileDAO.countOwnedFileByMember", p);
            if (owned == null || owned == 0) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "본인이 업로드한 이미지만 프로필로 설정할 수 있습니다.");
            }
        }
        MemberVO upd = new MemberVO();
        upd.setRowId(memberId);
        upd.setProfileFileId(normalized);
        commonDAO.update("memberDAO.updateProfileImage", upd);
    }

    /** 본인 닉네임 변경 — 다른 회원이 쓰는 닉네임과 중복 금지. */
    @Transactional
    public void changeNickname(NicknameRequest request) {
        String memberId = SecurityUtil.getCurrentMemberId();
        MemberVO chk = new MemberVO();
        chk.setNickname(request.nickname());
        chk.setMemberId(memberId);
        Integer cnt = commonDAO.selectOne("memberDAO.selectCountByNicknameExcept", chk);
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 사용 중인 닉네임입니다.");
        }
        MemberVO upd = new MemberVO();
        upd.setRowId(memberId);
        upd.setNickname(request.nickname());
        commonDAO.update("memberDAO.updateNickname", upd);
    }

    /** 비밀번호 만료 연장("나중에") — 본인 password_expire_dt 재형성. */
    public void pwExtend() {
        MemberVO param = new MemberVO();
        param.setMemberId(SecurityUtil.getCurrentMemberId());
        commonDAO.update("memberDAO.extendPwExpire", param);
    }

    /** 본인 비밀번호 변경 — 현재 비번 검증 후 변경(복잡도 검증은 컨트롤러에서 선행).
     *  변경 시 token_ver를 올려 기존 토큰(탈취 세션 포함) 전부 무효화 → 이후 재로그인 필요. */
    @Transactional
    public void pwChange(PwChangeRequest request) {
        String memberId = SecurityUtil.getCurrentMemberId();
        MemberVO param = new MemberVO();
        param.setMemberId(memberId);
        MemberVO user = commonDAO.selectOne("memberDAO.selectByMemberId", param);
        if (user == null || !passwordEncoder.matches(request.currentPw(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "현재 비밀번호가 일치하지 않습니다.");
        }
        MemberVO upd = new MemberVO();
        upd.setRowId(memberId);
        upd.setPassword(passwordEncoder.encode(request.newPw()));
        commonDAO.update("memberDAO.updatePw", upd);
        commonDAO.selectOne("memberDAO.incrementTokenVer", param); // 비번 변경 → 세션 무효화
    }

    /** Access 만료 시 Refresh 토큰으로 재발급 */
    public TokenResponse refresh(RefreshRequest request) {
        String rt = request.refreshToken();
        if (!jwtTokenProvider.validate(rt) || !"refresh".equals(jwtTokenProvider.getType(rt))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        String memberId = jwtTokenProvider.getMemberId(rt);
        MemberVO param = new MemberVO();
        param.setMemberId(memberId);
        MemberVO user = commonDAO.selectOne("memberDAO.selectByMemberId", param);
        if (user == null || !"Y".equals(user.getUseYn())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        // 정지/잠금 계정은 유효한 refresh 토큰을 갖고 있어도 재발급 차단(로그인과 동일 기준)
        if ("STATUS03".equals(user.getStatusCd())) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if ("STATUS02".equals(user.getStatusCd()) && "Y".equals(user.getLockActive())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        // 토큰 버전 대조: 이후 새 로그인/로그아웃으로 token_ver가 올라갔으면 이 refresh는 폐기된 세션 → 재발급 차단
        String rtVer = jwtTokenProvider.getVer(rt);
        if (rtVer == null || !rtVer.equals(user.getTokenVer())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        // 재발급은 같은 세션 유지 → token_ver 그대로(증가시키지 않음)
        String accessToken = jwtTokenProvider.createAccessToken(memberId, user.getTypeCd(), user.getTokenVer());
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId, user.getTokenVer());
        return new TokenResponse(accessToken, refreshToken);
    }

    /** 로그아웃 — token_ver +1로 현재 계정에 발급된 모든 토큰(access·refresh)을 즉시 무효화. */
    public void logout() {
        commonDAO.selectOne("memberDAO.incrementTokenVer", memberIdParam(SecurityUtil.getCurrentMemberId()));
    }

    /** memberId만 담은 조회/갱신용 파라미터 VO */
    private MemberVO memberIdParam(String memberId) {
        MemberVO v = new MemberVO();
        v.setMemberId(memberId);
        return v;
    }
}
