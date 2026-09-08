package com.pwsh.domain.loginsession.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 접속 세션 VO (login_session). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginSessionVO extends BaseVO {

    // PK(login_session_id)는 BaseVO.rowId로 통일
    private String memberId;
    private String memberName; // 목록 표시용(member 조인, 복호화)
    private String deviceType;
    private String userAgent;
    private String loginDt;
    private String lastSeenDt;
    private String endDt;
    private String endReason;

    /** 조회 결과 파생값 — 'Y'면 아직 열려 있고 만료창 안(=현재 접속 중). */
    private String activeYn;
    /** 목록 필터: ACTIVE=접속중만 / ENDED=종료만 / 빈값=전체 */
    private String filterStatus;
}
