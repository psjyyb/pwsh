package com.pwsh.domain.mail.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 메일 — 템플릿 관리 + 발송 + 이력. 컨트롤러는 매핑만, 로직·트랜잭션은 여기(도메인 단일 @Service).
 *
 * <p>발송 지점은 <b>코드(templateCd)</b>로만 템플릿을 찾는다 — 문구를 고치려고 배포하지 않기 위함.
 * 보낸 것은 성공·실패·미발송을 가리지 않고 전부 {@code mail_log}에 남긴다("보냈는데 안 왔다"는
 * 문의를 가릴 수 있는 유일한 근거다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    /** 본문·제목의 치환 자리 — {{키}} */
    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*\\}\\}");

    private final CommonDAO commonDAO;
    private final JavaMailSender mailSender;
    private final PlatformTransactionManager txManager;

    /** false면 실제 전송 없이 이력만 SKIP으로 남긴다(개발·테스트 기본값). */
    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${mail.log.retention-days:90}")
    private int logRetentionDays;

    // ===== 템플릿 =====

    public List<MailTemplateVO> selectTemplateList(MailTemplateVO vo) {
        return commonDAO.selectList("mailTemplateDAO.selectList", vo);
    }

    public int selectTemplateListTotalCount(MailTemplateVO vo) {
        return commonDAO.selectOne("mailTemplateDAO.selectListTotalCount", vo);
    }

    public MailTemplateVO selectTemplateView(MailTemplateVO vo) {
        return commonDAO.selectOne("mailTemplateDAO.selectView", vo);
    }

    public void insertTemplate(MailTemplateVO vo) {
        assertCodeFree(vo);
        commonDAO.insert("mailTemplateDAO.insert", vo);
    }

    public void updateTemplate(MailTemplateVO vo) {
        assertCodeFree(vo);
        commonDAO.update("mailTemplateDAO.update", vo);
    }

    public void deleteTemplate(MailTemplateVO vo) {
        commonDAO.delete("mailTemplateDAO.delete", vo);
    }

    /**
     * 같은 코드의 사용중 템플릿이 이미 있으면 거부.
     *
     * <p>DB 유니크 인덱스가 최종 방어선이지만, 그대로 두면 제약 위반이 500으로 나간다.
     * 운영자가 "무엇이 잘못됐는지" 알 수 있게 여기서 400으로 돌려준다.
     */
    private void assertCodeFree(MailTemplateVO vo) {
        MailTemplateVO exists = commonDAO.selectOne("mailTemplateDAO.selectByCode", vo);
        if (exists != null && !exists.getRowId().equals(vo.getRowId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 사용중인 템플릿 코드입니다: " + vo.getTemplateCd());
        }
    }

    // ===== 이력 =====

    public List<MailLogVO> selectLogList(MailLogVO vo) {
        return commonDAO.selectList("mailLogDAO.selectList", vo);
    }

    public int selectLogListTotalCount(MailLogVO vo) {
        return commonDAO.selectOne("mailLogDAO.selectListTotalCount", vo);
    }

    public MailLogVO selectLogView(MailLogVO vo) {
        return commonDAO.selectOne("mailLogDAO.selectView", vo);
    }

    // ===== 발송 =====

    /**
     * 템플릿으로 메일 1통 발송. 성공하면 true.
     *
     * <p><b>예외를 던지지 않는다</b> — 메일 실패로 업무 트랜잭션(가입·주문 등)까지 되돌리면
     * 사용자는 "가입이 안 됐다"고 느낀다. 실패를 반드시 알아야 하는 호출자(이메일 인증 등)는
     * 반환값을 검사해 직접 중단시킨다.
     *
     * @param templateCd 템플릿 코드(mail_template.template_cd)
     * @param toEmail    수신자
     * @param vars       {{키}} 치환 값. null 가능
     */
    public boolean send(String templateCd, String toEmail, Map<String, String> vars) {
        MailTemplateVO key = new MailTemplateVO();
        key.setTemplateCd(templateCd);
        MailTemplateVO tpl = commonDAO.selectOne("mailTemplateDAO.selectByCode", key);
        if (tpl == null) {
            // 템플릿이 없으면 보낼 내용이 없다. 조용히 지나가면 아무도 모르므로 이력에 남긴다.
            writeLog(templateCd, toEmail, null, null, "FAIL", "템플릿을 찾을 수 없습니다: " + templateCd);
            log.warn("메일 템플릿 없음: {}", templateCd);
            return false;
        }
        Map<String, String> values = vars == null ? Map.of() : vars;
        List<String> missing = new ArrayList<>();
        String subject = render(tpl.getSubject(), values, missing);
        String content = render(tpl.getContent(), values, missing);
        return dispatch(templateCd, toEmail, subject, content, missing);
    }

    /**
     * 실제 전송 + 이력 적재. mail.enabled=false면 전송하지 않고 SKIP으로만 남긴다.
     * 미치환 키가 있으면 발송은 하되 경고로 이력에 적는다(수신자에게 {{키}}가 보이는 것보다 낫다).
     */
    private boolean dispatch(String templateCd, String toEmail, String subject, String content,
                             List<String> missing) {
        String warn = missing.isEmpty() ? null : "미치환 키: " + String.join(", ", missing);
        if (!mailEnabled) {
            writeLog(templateCd, toEmail, subject, content, "SKIP",
                    warn == null ? "mail.enabled=false" : "mail.enabled=false / " + warn);
            return false;
        }
        if (mailFrom == null || mailFrom.isBlank()) {
            // 발송을 켜 놓고 계정을 안 넣은 상태. 조용히 성공한 척하지 않는다.
            writeLog(templateCd, toEmail, subject, content, "FAIL", "발신 계정(MAIL_USERNAME) 미설정");
            return false;
        }
        try {
            // HTML 메일이라 MimeMessage(+Helper). 평문 SimpleMailMessage로는 스타일이 적용되지 않는다.
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(mime);
            writeLog(templateCd, toEmail, subject, content, "SUCCESS", warn);
            return true;
        } catch (Exception e) {
            log.warn("메일 발송 실패 template={} to={}", templateCd, toEmail, e);
            writeLog(templateCd, toEmail, subject, content, "FAIL", shorten(e.getMessage()));
            return false;
        }
    }

    /**
     * 이력 적재. 호출한 업무 트랜잭션이 롤백돼도 <b>발송 사실은 남아야 하므로</b> 독립 트랜잭션이다
     * (메일은 이미 SMTP 서버로 나갔고, 그건 되돌릴 수 없다).
     *
     * <p>{@code @Transactional(REQUIRES_NEW)}을 쓰지 않는 이유: 이 메서드는 같은 클래스의
     * {@code dispatch()}가 부른다. 자기 호출은 프록시를 타지 않아 애너테이션이 <b>조용히 무시</b>되고
     * 이력이 업무 트랜잭션과 함께 롤백된다. TransactionTemplate은 호출 경로와 무관하게 동작한다.
     */
    public void writeLog(String templateCd, String toEmail, String subject, String content,
                         String statusCd, String errorMsg) {
        MailLogVO logVo = new MailLogVO();
        logVo.setTemplateCd(templateCd);
        logVo.setToEmail(toEmail);
        logVo.setSubject(subject);
        logVo.setContent(content);
        logVo.setStatusCd(statusCd);
        logVo.setErrorMsg(shorten(errorMsg));

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> commonDAO.insert("mailLogDAO.insert", logVo));
    }

    /**
     * {{키}} 치환. 값은 <b>HTML 이스케이프 후</b> 넣는다 — 회원 이름 같은 사용자 입력이 그대로
     * 본문에 들어가면 메일 본문이 곧 스크립트 주입 통로가 된다.
     * 값이 없는 키는 지우고(수신자에게 {{키}}가 보이는 것보다 낫다) 이름을 {@code missing}에 모은다.
     */
    private String render(String template, Map<String, String> vars, List<String> missing) {
        if (template == null) {
            return "";
        }
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = vars.get(key);
            if (value == null) {
                missing.add(key);
                value = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(escapeHtml(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** error_msg 컬럼 길이(500) 보호 — 긴 SMTP 스택 메시지가 그대로 들어오면 insert가 깨진다. */
    private String shorten(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    /** 템플릿 미리보기(발송 없이 치환 결과만). 관리화면에서 저장 전에 확인한다. */
    public Map<String, String> preview(String templateCd, Map<String, String> vars) {
        MailTemplateVO key = new MailTemplateVO();
        key.setTemplateCd(templateCd);
        MailTemplateVO tpl = commonDAO.selectOne("mailTemplateDAO.selectByCode", key);
        if (tpl == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, String> values = vars == null ? Map.of() : vars;
        List<String> missing = new ArrayList<>();
        Map<String, String> out = new LinkedHashMap<>();
        out.put("subject", render(tpl.getSubject(), values, missing));
        out.put("content", render(tpl.getContent(), values, missing));
        out.put("missing", String.join(", ", missing));
        return out;
    }

    // ===== 이력 정리 =====

    /**
     * 보존기간 지난 이력 삭제. 본문에 인증번호·개인정보가 남으므로 무한 보관하지 않는다.
     * retention-days=0이면 정리하지 않는다(보관 정책을 직접 관리하는 경우).
     */
    public int purgeLogs(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        return commonDAO.delete("mailLogDAO.deleteExpired", Map.of("days", String.valueOf(retentionDays)));
    }

    @Scheduled(cron = "${mail.log.cron:0 30 4 * * *}")
    public void scheduledPurge() {
        int deleted = purgeLogs(logRetentionDays);
        if (deleted > 0) {
            log.info("메일 발송 이력 정리: {}건 삭제(보존 {}일)", deleted, logRetentionDays);
        }
    }
}
