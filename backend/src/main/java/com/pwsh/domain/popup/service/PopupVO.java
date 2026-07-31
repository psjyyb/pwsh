package com.pwsh.domain.popup.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 팝업 VO (t_popup). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PopupVO extends BaseVO {

    // PK(pop_id)는 BaseVO.dbKey로 통일
    private String popNm;
    private String startDt;
    private String endDt;
    private String link;
    private String txt;
    private String ordr;
    private String popWidth;
    private String popHeight;
    private String popTop;
    private String popLeft;
    private String fileId; // 팝업 이미지(t_file)
}
