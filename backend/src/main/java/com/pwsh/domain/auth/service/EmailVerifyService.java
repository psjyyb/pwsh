package com.pwsh.domain.auth.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.mail.service.MailService;
import java.security.SecureRandom;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이메일 인증 코드 발급·검증 — 가입(SIGNUP, target=이메일)과 비밀번호 찾기(RESET, target=회원 ID) 공용.
 *
 * <p>CMS 1.2.35에서 보강한 점: ① 코드는 BCrypt 해시로만 저장(평문이면 DB를 읽을 수 있는 사람이 남의 가입·재설정을 대신한다)
 * ② 메일 발송에 실패하면 행을 지우지 않고 <b>바로 만료</b>시킨다 — 지우면(v0.6.34까지는 발급째 롤백) 재발송 제한(30초)의 근거가
 * 사라져, 실패가 이어질 때 제한 없이 다시 보낼 수 있다 ③ 같은 대상의 발급은 DB 잠금으로 한 줄로 세운다.
 *
 * <p>메일 본문·제목은 여기 없다 — {@link MailService}가 {@code mail_template}(SIGNUP_CODE·RESET_CODE)에서 꺼낸다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerifyService {

    public static final String SIGNUP = "SIGNUP";
    public static final String RESET = "RESET";

    /** 코드 유효 시간(분) — 가입·비밀번호 찾기 화면의 안내(CODE_TTL_MIN)와 같은 값 */
    public static final int TTL_MIN = 5;
    /** 틀릴 수 있는 횟수 — 6자리라 제한이 없으면 맞힐 때까지 시도할 수 있다 */
    private static final int MAX_ATTEMPT = 5;
    /** 재발송 최소 간격(초) — 메일 폭탄·발송 남용 완화 */
    private static final int RESEND_COOLDOWN_SEC = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CommonDAO commonDAO;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager txManager;

    /**
     * 코드 발급 + 메일. 재발송 제한 안이면 400. 메일 발송에 실패하면 코드를 만료시키고 500(사용자가 다시 시도할 수 있게 알린다).
     *
     * @param target  가입=이메일, 재설정=회원 ID
     * @param toEmail 받는 주소
     */
    public void issue(String target, String purpose, String toEmail) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerificationVO vo = new EmailVerificationVO();
        vo.setTarget(target);
        vo.setPurpose(purpose);
        vo.setCodeHash(passwordEncoder.encode(code));
        vo.setTtlMin(String.valueOf(TTL_MIN));
        vo.setCooldownSec(String.valueOf(RESEND_COOLDOWN_SEC));

        // 발급은 먼저 커밋한다 — 메일은 되돌릴 수 없으니 발송 결과와 묶지 않는다(실패는 아래에서 만료로 처리)
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        boolean issued = Boolean.TRUE.equals(tx.execute(status -> {
            commonDAO.selectOne("emailVerifyDAO.lockTarget", vo);
            Integer recent = commonDAO.selectOne("emailVerifyDAO.countRecent", vo);
            if (recent != null && recent > 0) {
                return false;
            }
            commonDAO.delete("emailVerifyDAO.deleteBeforeIssue", vo);
            commonDAO.insert("emailVerifyDAO.insert", vo);
            return true;
        }));
        if (!issued) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.email.resendCooldown", RESEND_COOLDOWN_SEC));
        }
        String templateCd = SIGNUP.equals(purpose) ? "SIGNUP_CODE" : "RESET_CODE";
        String title = commonDAO.selectOne("configDAO.selectTitle", vo);
        boolean sent = mailService.send(templateCd, toEmail, Map.of(
                "siteTitle", title == null ? "" : title, "code", code, "ttl", String.valueOf(TTL_MIN)));
        if (!sent) {
            // 사용자가 받지 못한 코드를 유효하게 두면 "인증번호가 안 온다"가 계속 반복된다. 행은 재발송 제한 근거로 남긴다
            commonDAO.update("emailVerifyDAO.expire", vo);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, Messages.get("error.email.sendFailed"));
        }
    }

    /**
     * 코드가 맞는지(최신·미만료 1건과 비교). 5번 틀리면 그 코드는 버린다. 맞아도 지우지 않는다(호출자가 처리를 끝낸 뒤 consume).
     *
     * <p>★ 독립 트랜잭션 — 틀린 횟수는 호출자(가입·재설정)가 실패로 되돌려도 남아야 한다. 같이 롤백되면 횟수 제한이 무의미하다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verify(String target, String purpose, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        EmailVerificationVO key = new EmailVerificationVO();
        key.setTarget(target);
        key.setPurpose(purpose);
        EmailVerificationVO row = commonDAO.selectOne("emailVerifyDAO.selectLatestValid", key);
        if (row == null) {
            return false;
        }
        if (Integer.parseInt(row.getAttemptCnt()) >= MAX_ATTEMPT) {
            commonDAO.delete("emailVerifyDAO.deleteByTarget", key);
            return false;
        }
        if (passwordEncoder.matches(code.trim(), row.getCodeHash())) {
            return true;
        }
        commonDAO.update("emailVerifyDAO.incAttempt", row);
        return false;
    }

    /** 처리를 끝낸 뒤 그 대상·용도의 코드를 지운다(재사용 방지). */
    public void consume(String target, String purpose) {
        EmailVerificationVO vo = new EmailVerificationVO();
        vo.setTarget(target);
        vo.setPurpose(purpose);
        commonDAO.delete("emailVerifyDAO.deleteByTarget", vo);
    }
}
