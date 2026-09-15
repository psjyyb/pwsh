package com.pwsh.domain.auth.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.mail.service.MailService;
import java.security.SecureRandom;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증코드 발급·검증·발송. 가입 이메일 인증(SIGNUP)과 비밀번호 재설정(RESET) 공용.
 * 6자리 숫자 코드, 유효기간 {@value #TTL_MIN}분, 검증 성공 시 삭제(소비).
 *
 * <p>메일 본문·제목은 여기 있지 않다 — {@link MailService}가 {@code mail_template}에서 꺼내 쓴다
 * (문구를 고치려고 배포하지 않기 위함). 발송 결과는 {@code mail_log}에 그대로 남는다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerifyService {

    /** 코드 유효기간(분) */
    private static final int TTL_MIN = 5;
    /** 코드 검증 실패 허용 횟수(초과 시 코드 폐기 — 무차별 대입 차단) */
    private static final int MAX_ATTEMPT = 5;
    /** 재발송 최소 간격(초) — 메일 폭탄/발송 남용 완화 */
    private static final int RESEND_COOLDOWN_SEC = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CommonDAO commonDAO;
    private final MailService mailService;

    /**
     * 코드 발급 + 메일 발송. target=식별키(가입:이메일, 재설정:member_id), toEmail=수신 이메일.
     * 기존 코드는 삭제 후 재발급(항상 최신 1건만 유효).
     */
    @Transactional
    public void issue(String target, String purpose, String toEmail) {
        EmailVerificationVO throttleKey = new EmailVerificationVO();
        throttleKey.setTarget(target);
        throttleKey.setPurpose(purpose);
        // 재발송 throttle: 최근 발송 후 일정 시간 안 지났으면 거부(메일 폭탄/SMTP 남용 완화)
        Integer sinceLast = commonDAO.selectOne("emailVerifyDAO.selectSecondsSinceLast", throttleKey);
        if (sinceLast != null && sinceLast < RESEND_COOLDOWN_SEC) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    Messages.get("error.email.resendCooldown", RESEND_COOLDOWN_SEC));
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerificationVO vo = new EmailVerificationVO();
        vo.setTarget(target);
        vo.setPurpose(purpose);
        vo.setCode(code);
        vo.setTtlMin(String.valueOf(TTL_MIN));
        commonDAO.delete("emailVerifyDAO.deleteByTarget", vo);
        commonDAO.insert("emailVerifyDAO.insert", vo);
        sendCodeMail(toEmail, code, purpose);
    }

    /**
     * 코드 일치(미만료) 여부. 무차별 대입 차단: 실패 {@value #MAX_ATTEMPT}회 초과 시 코드 폐기.
     * 성공 시 삭제하지 않는다(상위 로직에서 consume 호출).
     * REQUIRES_NEW: 실패 시도 카운터(incAttempt)는 호출자(signup/resetPassword)가 코드 불일치로
     * 롤백해도 반드시 남아야 하므로 독립 트랜잭션으로 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verify(String target, String purpose, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        EmailVerificationVO vo = new EmailVerificationVO();
        vo.setTarget(target);
        vo.setPurpose(purpose);
        // 시도 횟수 초과 → 코드 폐기(더 이상 맞춰도 무효). 재발급 필요.
        Integer attempts = commonDAO.selectOne("emailVerifyDAO.selectAttemptCnt", vo);
        if (attempts != null && attempts >= MAX_ATTEMPT) {
            commonDAO.delete("emailVerifyDAO.deleteByTarget", vo);
            return false;
        }
        vo.setCode(code);
        Integer cnt = commonDAO.selectOne("emailVerifyDAO.selectValidCount", vo);
        if (cnt != null && cnt > 0) {
            return true;
        }
        // 불일치 → 실패 시도 +1 (누적치가 한도 도달하면 다음 시도부터 위에서 폐기)
        commonDAO.update("emailVerifyDAO.incAttempt", vo);
        return false;
    }

    /** 대상+용도 코드 전량 삭제(검증 성공 후 재사용 방지). */
    @Transactional
    public void consume(String target, String purpose) {
        EmailVerificationVO vo = new EmailVerificationVO();
        vo.setTarget(target);
        vo.setPurpose(purpose);
        commonDAO.delete("emailVerifyDAO.deleteByTarget", vo);
    }

    /**
     * 인증 메일 발송. 실패하면 예외를 던져 코드 발급까지 롤백한다 —
     * 사용자가 받지 못한 코드를 유효한 것처럼 남겨두면 "인증번호가 안 온다"가 계속 반복된다.
     * (발송 이력 자체는 독립 트랜잭션이라 롤백돼도 mail_log에 남는다.)
     */
    private void sendCodeMail(String to, String code, String purpose) {
        String templateCd = "SIGNUP".equals(purpose) ? "SIGNUP_CODE" : "RESET_CODE";
        boolean sent = mailService.send(templateCd, to,
                Map.of("code", code, "ttl", String.valueOf(TTL_MIN)));
        if (!sent) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, Messages.get("error.email.sendFailed"));
        }
    }

}
