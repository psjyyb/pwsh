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
}
