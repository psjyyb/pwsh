package com.pwsh.domain.searchstat.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 검색 통계 VO (search_keyword_daily 집계 · search_keyword_hide). 필드는 String 통일. */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchStatVO extends BaseVO {

    private String keyword;      // 정리한 검색어
    private String keywordLike;  // 관리 화면 검색용 — LIKE 특수문자 이스케이프본(원문은 filterKeyword)
    private String searchCnt;    // 기간 합계 검색 횟수
    private String zeroCnt;      // 그중 결과 0건 횟수
    private String lastDate;     // 마지막으로 검색된 날
    private String hiddenYn;     // 인기 검색어에서 숨김
    private String banYn;        // 금칙어가 들어 있음(서버가 계산)
    private String statDate;     // 일별 합계의 날짜
    private String keywordCnt;   // 일별 합계 — 그날 검색된 서로 다른 검색어 수
    private String days;         // 기간(오늘 포함 며칠)
    private String zeroOnly;     // Y=결과 없던 검색어만(결과 없던 횟수 많은 순)
    private String hideYn;       // 숨김 변경 요청 Y/N
    private String minCount;     // 인기 검색어 최소 횟수
    private String limitCnt;     // 인기 검색어 후보 수
}
