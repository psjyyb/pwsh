package com.pwsh.domain.report.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 신고 VO (t_report). PK(report_id)는 BaseVO.dbKey. 신고자는 reg_id(서버 세팅). */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportVO extends BaseVO {

    private String targetType; // BBS/COMMENT/RECRUIT
    private String targetId;   // 대상 PK
    private String reasonCd;   // 신고 사유 분류(t_code REPORT00)
    private String reasonNm;   // 분류명(조회)
    private String reason;     // 신고 사유(상세)
    private String status;     // PENDING/RESOLVED/DISMISSED
    // 조회 표시용(관리자 목록)
    private String targetTitle; // 대상 제목/발췌(조인)
    private String linkUrl;     // 대상으로 이동할 경로(관리자 확인용)
    private String regNm;       // 신고자 닉네임
}
