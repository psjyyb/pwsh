package com.pwsh.domain.loginlog.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 로그인 이력 VO (login_log). 성공·실패 모두. use_yn·upd 컬럼 없음(append-only). */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginLogVO extends BaseVO {

    // PK(login_log_id)는 BaseVO.rowId로 통일
    private String loginId;      // 입력한 아이디 그대로(없는 아이디도)
    private String resultCd;     // 결과(code LOGIN00)
    private String resultName;   // 결과 한글명(code 조인, 조회 전용)
    private String failMessage;  // 실패 시 사용자에게 나간 안내문
    private String deviceType;   // desktop/mobile/tablet
    private String userAgent;    // User-Agent 원문

    // 기간 검색(YYYY-MM-DD)
    private String filterFrom;
    private String filterTo;
    private String keywordLike;  // filterKeyword의 LIKE 이스케이프본(서비스 세팅)
}
