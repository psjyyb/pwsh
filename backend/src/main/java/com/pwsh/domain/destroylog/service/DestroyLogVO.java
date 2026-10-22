package com.pwsh.domain.destroylog.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 개인정보 파기 이력 VO (destroy_log). use_yn·upd 컬럼 없음(append-only). */
@Data
@EqualsAndHashCode(callSuper = true)
public class DestroyLogVO extends BaseVO {

    // PK(destroy_log_id)는 BaseVO.rowId로 통일
    private String memberId;     // 파기된 회원 ID(파기 후에도 member에 남는 키)
    private String destroyCd;    // 파기 유형(code DESTROY00)
    private String destroyName;  // 파기 유형 한글명(code 조인, 조회 전용)
    private String withdrawDt;   // 탈퇴 시각(즉시 파기면 없을 수 있음)
    private String actorId;      // 처리자(관리자 즉시 파기). 자동이면 null

    // 기간 검색(YYYY-MM-DD)
    private String filterFrom;
    private String filterTo;
    private String keywordLike;  // filterKeyword의 LIKE 이스케이프본(서비스 세팅)
}
