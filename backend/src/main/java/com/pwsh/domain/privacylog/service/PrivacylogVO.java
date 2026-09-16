package com.pwsh.domain.privacylog.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 개인정보 접근 로그 VO (privacy_log). PK(privacy_log_id)는 BaseVO.rowId.
 *
 * <p>append-only. 개인정보를 <b>읽은</b> 요청 1건 = 이 행 1건.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PrivacylogVO extends BaseVO {

    /** 조회한 사람(취급자) */
    private String memberId;
    /** 수행 업무 — 요청 경로 */
    private String requestUri;
    /** 개인정보를 읽은 매퍼 sql_id(쉼표 구분) */
    private String sqlIds;
    /** 처리한 정보주체(회원 ID). 많으면 앞부분 + "외 n명" */
    private String targetIds;
    private String accessCnt;
    private String deviceType;
    private String userAgent;
}
