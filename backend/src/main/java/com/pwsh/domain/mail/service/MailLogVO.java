package com.pwsh.domain.mail.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 메일 발송 이력 VO (mail_log). PK(mail_log_id)는 BaseVO.rowId.
 *
 * <p>append-only. 수신자 주소는 member.email과 같은 방식으로 암호화 저장하고 조회 시 복호화한다.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MailLogVO extends BaseVO {

    /** 사용한 템플릿 코드(템플릿 없이 직접 발송하면 null) */
    private String templateCd;
    private String toEmail;
    private String subject;
    private String content;
    /** 발송 결과 — SUCCESS / FAIL / SKIP (code MAIL00) */
    private String statusCd;
    private String statusName;
    /** 실패 사유 또는 경고(치환되지 않은 키 등) */
    private String errorMsg;

    /** 발송 요청 시 쓰는 치환 값(JSON 오브젝트). 저장 컬럼이 아니라 요청 전용 */
    private java.util.Map<String, String> vars;
}
