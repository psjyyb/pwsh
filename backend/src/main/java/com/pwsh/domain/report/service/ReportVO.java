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
    private String reason;     // 신고 사유
    private String status;     // PENDING/RESOLVED/DISMISSED
    // 조회 표시용(관리자 목록)
    private String targetTitle; // 대상 제목/발췌(조인)
    private String regNm;       // 신고자 닉네임
}
