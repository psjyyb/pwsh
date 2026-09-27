package com.pwsh.domain.page.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 페이지 VO (page). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageVO extends BaseVO {

    // PK(page_id)는 BaseVO.rowId로 통일 (조회 결과 별칭 + WHERE 바인딩)
    private String title;
    private String content;
    /** 통합검색 전용: 본문에서 찾았을 때 매칭 지점 주변 발췌(태그 제거된 평문). 제목에서 찾았으면 null */
    private String searchSnippet;
}
