package com.pwsh.domain.batch.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 배치 작업 VO (batch_job). PK(batch_job_id)는 BaseVO.rowId. 행은 코드에 구현된 작업과 1:1이라 추가·삭제가 없다. */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchJobVO extends BaseVO {

    private String jobCd;          // 작업 코드(BatchService가 이 값으로 실행할 코드를 고른다)
    private String name;
    private String description;
    private String cronExpr;       // Spring cron 6자리
    private String autoYn;         // 자동 실행(Y/N)
    private String sortNo;

    // 조회 전용 — 마지막 실행(batch_log 최신 1건)
    private String lastStatusCd;
    private String lastStatusName;
    private String lastManualYn;
    private String lastStartDt;
    private String lastEndDt;
    private String lastResultMsg;

    // 조회 전용 — 서비스가 채운다(DB 값이 아니다)
    private String nextRunDt;      // 다음 예약 실행 시각(자동 실행이 꺼져 있으면 null)
    private String scheduledYn;    // 지금 이 서버의 스케줄러에 등록돼 있는지
}
