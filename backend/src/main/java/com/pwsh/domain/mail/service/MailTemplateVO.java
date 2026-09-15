package com.pwsh.domain.mail.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 메일 템플릿 VO (mail_template). PK(mail_template_id)는 BaseVO.rowId.
 *
 * <p>{@code subject}·{@code content}의 {@code {{키}}}는 발송 시 치환된다 —
 * 치환 값은 HTML 이스케이프 후 삽입하므로 태그를 넣어도 글자로 보인다(메일 본문 XSS 차단).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MailTemplateVO extends BaseVO {

    /** 발송 코드가 참조하는 템플릿 코드(사용중인 것끼리 중복 불가) */
    private String templateCd;
    private String name;
    private String subject;
    /** HTML 본문 */
    private String content;
    /** 쓸 수 있는 치환 키 안내(운영자 메모 — 동작에는 영향 없음) */
    private String variables;

    /** 미리보기 요청의 치환 값(JSON 오브젝트). 저장 컬럼이 아니라 요청 전용 */
    private java.util.Map<String, String> vars;
}
