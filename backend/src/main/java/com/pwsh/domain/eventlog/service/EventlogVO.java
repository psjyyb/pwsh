package com.pwsh.domain.eventlog.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 이벤트(행위) 로그 VO (event_log). 로그인/등록/수정/삭제 기록. use_yn·upd 컬럼 없음(append-only). */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventlogVO extends BaseVO {

    // PK(event_log_id)는 BaseVO.rowId로 통일
    private String eventCd;    // 행위(code EVENT00): LOGIN/INSERT/UPDATE/DELETE
    private String eventName;  // 행위 한글명(code.code_name 조인, 조회 전용)
    private String memberId;       // 수행자(FK)
    private String targetTable;  // 대상 테이블(예: member). 로그인은 null
    private String targetId;     // 대상 행 PK. 로그인은 null
    private String deviceType;   // desktop/mobile/tablet
    private String userAgent;    // User-Agent 원문
}
