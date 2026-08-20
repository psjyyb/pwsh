package com.pwsh.domain.popup.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 팝업 VO (popup). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PopupVO extends BaseVO {

    // PK(popup_id)는 BaseVO.rowId로 통일
    private String popupName;
    private String startDt;
    private String endDt;
    private String linkUrl;
    private String content;
    private String sortNo;
    private String width;
    private String height;
    private String posTop;
    private String posLeft;
    private String fileId; // 팝업 이미지(file)
}
