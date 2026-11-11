package com.pwsh.domain.batch.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 배치 실행 이력 VO (batch_log). PK(batch_log_id)는 BaseVO.rowId. reg_id·reg_ip는 AuditInterceptor가 채운다. */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchLogVO extends BaseVO {

    private String jobCd;
    private String jobName;      // 조회 전용(batch_job 조인)
    private String manualYn;     // Y=즉시 실행, N=예약 실행
    private String statusCd;     // code BATCH00
    private String statusName;   // 조회 전용(code 조인)
    private String resultMsg;
    private String startDt;
    private String endDt;
}
