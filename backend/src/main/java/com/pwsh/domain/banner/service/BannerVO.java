package com.pwsh.domain.banner.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 배너 VO (banner). PK(banner_id)는 BaseVO.rowId.
 *
 * <p>title의 {@code [[...]]}는 강조 구간, 줄바꿈(\n)은 그대로 표시한다 — 프론트가 해석하는
 * <b>마커</b>이고 HTML이 아니다(저장값을 HTML로 렌더하면 XSS 통로가 된다).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BannerVO extends BaseVO {

    private String title;
    private String description;
    private String btn1Label;
    private String btn1Url;
    private String btn2Label;
    private String btn2Url;
    private String startDt;
    private String endDt;
    private String sortNo;
    /** 배경 이미지(file). file_ref의 file_type='BANNER'로 연결 */
    private String fileId;
}
