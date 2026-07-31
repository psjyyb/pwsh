package com.pwsh.domain.auth.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.eventlog.service.EventLogService;
import com.pwsh.domain.user.service.UserVO;
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

    public TokenResponse login(LoginRequest request) {
        // 계정 상태 사전 점검: 정지=차단, 잠금=시간 미경과면 차단 / 경과면 자동 해제
        UserVO pre = commonDAO.selectOne("userDAO.selectByUserId", userIdParam(request.userId()));
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
                commonDAO.update("userDAO.unlockAccount", userIdParam(request.userId())); // 잠금시간 경과 → 자동 해제
            }
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.userId(), request.userPw()));
        } catch (BadCredentialsException e) {
            if (pre == null) {
                throw e; // 존재하지 않는 계정 → 일반 인증 실패
            }
            // 비밀번호 불일치 → 실패횟수 +1(제한 도달 시 자동 잠금), 상태 다시 조회해 안내
            commonDAO.update("userDAO.increaseFailCnt", userIdParam(request.userId()));
            UserVO after = commonDAO.selectOne("userDAO.selectByUserId", userIdParam(request.userId()));
            if (after == null) {
                throw e; // 재조회 사이 계정이 사라진 극단적 레이스 → 일반 인증 실패로 처리(NPE 방지)
            }
            if ("STATUS02".equals(after.getStatusCd())) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                        "비밀번호를 " + after.getFailCntLimit() + "회 틀려 계정이 " + after.getFailCntDeniedTi()
                                + "분간 잠겼습니다. " + after.getFailCntDeniedTi() + "분 후 다시 시도해 주세요.");
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "비밀번호가 일치하지 않습니다. (" + after.getFailCnt() + "/" + after.getFailCntLimit()
                            + "회) " + after.getFailCntLimit() + "회 틀리면 계정이 " + after.getFailCntDeniedTi() + "분간 잠깁니다.");
        }
        // 접속로그 audit(reg_id=로그인 사용자)을 위해 컨텍스트 설정 (STATELESS라 요청 종료 시 사라짐)
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        // 새 세션 시작: token_ver +1 → 다른 기기에 남아있던 토큰은 다음 요청에서 무효(단일세션 last-wins)
        String newVer = String.valueOf(
                (Integer) commonDAO.selectOne("userDAO.incrementTokenVer", userIdParam(userDetails.getUserId())));
        String accessToken = jwtTokenProvider.createAccessToken(userDetails.getUserId(), userDetails.getMemCd(), newVer);
        String refreshToken = jwtTokenProvider.createRefreshToken(userDetails.getUserId(), newVer);

        // 마지막 로그인 일시/IP 기록
        UserVO loginInfo = new UserVO();
        loginInfo.setUserId(userDetails.getUserId());
        loginInfo.setLastLoginIp(ClientIpHolder.get());
        commonDAO.update("userDAO.updateLoginInfo", loginInfo);

        eventLogService.write("LOGIN", null, null);

        // 비밀번호 만료 알림(강제 아님)
        boolean pwExpired = "Y".equals(userDetails.getPwExpired());
        Integer pwDaysLeft = userDetails.getPwDaysLeft() != null
                ? Integer.valueOf(userDetails.getPwDaysLeft()) : null;
        return new TokenResponse(accessToken, refreshToken, pwExpired, pwDaysLeft);
    }

    /**
     * 셀프 회원가입 — 아이디·닉네임 중복검사 후 t_user 생성 + MEMBER 권한그룹 매핑(같은 트랜잭션).
     * 익명 요청이라 audit reg_id는 AuditInterceptor가 "system"으로 세팅한다.
     * (비밀번호 복잡도는 컨트롤러에서 PasswordPolicy로 선검증)
     */
    @Transactional
    public void signup(SignupRequest request) {
        if (!request.userPw().equals(request.pwConfirm())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호 확인이 일치하지 않습니다.");
        }
        // 아이디 중복
        UserVO idCheck = new UserVO();
        idCheck.setUserId(request.userId());
        Integer idCnt = commonDAO.selectOne("userDAO.selectCount", idCheck);
        if (idCnt != null && idCnt > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 사용 중인 아이디입니다.");
        }
        // 닉네임 중복
        UserVO nickCheck = new UserVO();
        nickCheck.setNickname(request.nickname());
        Integer nickCnt = commonDAO.selectOne("userDAO.selectCountByNickname", nickCheck);
        if (nickCnt != null && nickCnt > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 사용 중인 닉네임입니다.");
        }
        // 생성 (일반회원 MEM01, 정상 STATUS01, 비번 BCrypt. 실명은 미수집 → user_nm null)
        UserVO user = new UserVO();
        user.setUserId(request.userId());
        user.setUserPw(passwordEncoder.encode(request.userPw()));
        user.setMemCd("MEM01");
        user.setNickname(request.nickname());
        user.setEmail(request.email()); // 선택(null 가능)
        user.setStatusCd("STATUS01");
        commonDAO.insert("userDAO.insert", user);
        // MEMBER 권한그룹 매핑
        UserVO authUser = new UserVO();
        authUser.setUserId(request.userId());
        authUser.setAuthgrpId("MEMBER");
        commonDAO.insert("userDAO.insertAuthUser", authUser);
    }

    /** 비밀번호 만료 연장("나중에") — 본인 pw_expire_dt 재형성. */
    public void pwExtend() {
        UserVO param = new UserVO();
        param.setUserId(SecurityUtil.getCurrentUserId());
        commonDAO.update("userDAO.extendPwExpire", param);
    }

    /** 본인 비밀번호 변경 — 현재 비번 검증 후 변경(복잡도 검증은 컨트롤러에서 선행).
     *  변경 시 token_ver를 올려 기존 토큰(탈취 세션 포함) 전부 무효화 → 이후 재로그인 필요. */
    @Transactional
    public void pwChange(PwChangeRequest request) {
        String userId = SecurityUtil.getCurrentUserId();
        UserVO param = new UserVO();
        param.setUserId(userId);
        UserVO user = commonDAO.selectOne("userDAO.selectByUserId", param);
        if (user == null || !passwordEncoder.matches(request.currentPw(), user.getUserPw())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "현재 비밀번호가 일치하지 않습니다.");
        }
        UserVO upd = new UserVO();
        upd.setDbKey(userId);
        upd.setUserPw(passwordEncoder.encode(request.newPw()));
        commonDAO.update("userDAO.updatePw", upd);
        commonDAO.selectOne("userDAO.incrementTokenVer", param); // 비번 변경 → 세션 무효화
    }

    /** Access 만료 시 Refresh 토큰으로 재발급 */
    public TokenResponse refresh(RefreshRequest request) {
        String rt = request.refreshToken();
        if (!jwtTokenProvider.validate(rt) || !"refresh".equals(jwtTokenProvider.getType(rt))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        String userId = jwtTokenProvider.getUserId(rt);
        UserVO param = new UserVO();
        param.setUserId(userId);
        UserVO user = commonDAO.selectOne("userDAO.selectByUserId", param);
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
        String accessToken = jwtTokenProvider.createAccessToken(userId, user.getMemCd(), user.getTokenVer());
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, user.getTokenVer());
        return new TokenResponse(accessToken, refreshToken);
    }

    /** 로그아웃 — token_ver +1로 현재 계정에 발급된 모든 토큰(access·refresh)을 즉시 무효화. */
    public void logout() {
        commonDAO.selectOne("userDAO.incrementTokenVer", userIdParam(SecurityUtil.getCurrentUserId()));
    }

    /** userId만 담은 조회/갱신용 파라미터 VO */
    private UserVO userIdParam(String userId) {
        UserVO v = new UserVO();
        v.setUserId(userId);
        return v;
    }
}
