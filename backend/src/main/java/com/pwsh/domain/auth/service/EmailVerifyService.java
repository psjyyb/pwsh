package com.pwsh.domain.auth.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증코드 발급·검증·발송. 가입 이메일 인증(SIGNUP)과 비밀번호 재설정(RESET) 공용.
 * 6자리 숫자 코드, 유효기간 {@value #TTL_MIN}분, 검증 성공 시 삭제(소비).
 * 발신 SMTP 계정은 환경변수(MAIL_USERNAME/MAIL_PASSWORD)로만 주입한다.
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
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    /**
     * 코드 발급 + 메일 발송. target=식별키(가입:이메일, 재설정:user_id), toEmail=수신 이메일.
     * 기존 코드는 삭제 후 재발급(항상 최신 1건만 유효).
     */
    @Transactional
    public void issue(String target, String purpose, String toEmail) {
        if (mailFrom == null || mailFrom.isBlank()) {
            // 발신 계정 미설정 → 실제 발송 불가(모킹 금지). 운영자가 환경변수 설정해야 함.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "메일 발송이 설정되지 않았습니다. 잠시 후 다시 시도하거나 관리자에게 문의하세요.");
        }
        EmailVerificationVO throttleKey = new EmailVerificationVO();
        throttleKey.setTarget(target);
        throttleKey.setPurpose(purpose);
        // 재발송 throttle: 최근 발송 후 일정 시간 안 지났으면 거부(메일 폭탄/SMTP 남용 완화)
        Integer sinceLast = commonDAO.selectOne("emailVerifyDAO.selectSecondsSinceLast", throttleKey);
        if (sinceLast != null && sinceLast < RESEND_COOLDOWN_SEC) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "인증코드는 " + RESEND_COOLDOWN_SEC + "초 후에 다시 요청할 수 있습니다.");
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

    private void sendCodeMail(String to, String code, String purpose) {
        boolean signup = "SIGNUP".equals(purpose);
        String subject = signup ? "[취만사] 회원가입 인증번호" : "[취만사] 비밀번호 재설정 인증번호";
        String title = signup ? "회원가입 인증번호" : "비밀번호 재설정 인증번호";
        String html = buildHtml(title, code);
        try {
            // HTML 메일 → MimeMessage(+MimeMessageHelper). 평문 SimpleMailMessage로는 스타일 불가.
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = HTML 본문
            mailSender.send(mime);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "인증 메일 발송에 실패했습니다. 이메일 주소를 확인하거나 잠시 후 다시 시도해 주세요.");
        }
    }

    /**
     * 인증번호 안내 HTML 본문. 이메일 클라이언트 호환을 위해 table 레이아웃 + 인라인 스타일만 사용.
     * (String.format은 CSS의 width:100% 등 %와 충돌하므로 replace로 치환)
     */
    private String buildHtml(String title, String code) {
        return HTML_TEMPLATE
                .replace("%TITLE%", title)
                .replace("%CODE%", code)
                .replace("%TTL%", String.valueOf(TTL_MIN));
    }

    private static final String HTML_TEMPLATE = ""
        + "<div style=\"margin:0;padding:0;background:#f4f2fb;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f2fb;padding:24px 0;\"><tr><td align=\"center\">"
        + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:480px;max-width:480px;background:#ffffff;border-radius:14px;overflow:hidden;font-family:'Malgun Gothic','Apple SD Gothic Neo',Arial,sans-serif;box-shadow:0 6px 24px rgba(80,60,160,.12);\">"
        // header
        + "<tr><td style=\"background:#8B72F5;padding:22px 28px;text-align:center;\">"
        + "<span style=\"color:#ffffff;font-size:20px;font-weight:700;\">&#128274; 인증번호 발송</span></td></tr>"
        // body
        + "<tr><td style=\"padding:32px 32px 8px 32px;color:#333333;font-size:14px;line-height:1.7;\">"
        + "안녕하세요, <b>취만사</b>입니다.<br/>요청하신 <b>%TITLE%</b>를 안내해 드립니다.</td></tr>"
        // code box
        + "<tr><td style=\"padding:16px 32px;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:2px dashed #B9A6F7;border-radius:12px;background:#faf9ff;\"><tr><td style=\"padding:22px;text-align:center;\">"
        + "<div style=\"color:#999999;font-size:13px;margin-bottom:10px;\">인증번호</div>"
        + "<div style=\"color:#6a4df4;font-size:34px;font-weight:800;letter-spacing:8px;\">%CODE%</div></td></tr></table></td></tr>"
        // instruction
        + "<tr><td style=\"padding:8px 32px;color:#555555;font-size:13px;line-height:1.7;\">"
        + "위 인증번호를 화면의 인증번호 입력란에 입력해 주세요.</td></tr>"
        // warning
        + "<tr><td style=\"padding:12px 32px 26px 32px;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fff8e1;border-radius:8px;\"><tr><td style=\"padding:14px 16px;color:#8a6d1b;font-size:12.5px;line-height:1.7;\">"
        + "&#9888; 본 인증번호는 발송 후 <b>%TTL%분간</b> 유효합니다.<br/>본인이 요청하지 않은 경우, 이 메일을 무시하셔도 됩니다.</td></tr></table></td></tr>"
        // footer
        + "<tr><td style=\"background:#f4f2fb;padding:18px 32px;text-align:center;color:#aaaaaa;font-size:11.5px;line-height:1.6;\">"
        + "본 메일은 발신전용입니다.<br/>COPYRIGHT &copy; 2026 취만사 (People Who Share Hobbies). ALL RIGHTS RESERVED.</td></tr>"
        + "</table></td></tr></table></div>";
}
