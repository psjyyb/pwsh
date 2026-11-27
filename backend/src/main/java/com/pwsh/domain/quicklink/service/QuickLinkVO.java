package com.pwsh.domain.quicklink.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 자주찾는 서비스 VO (quick_link, CMS 1.2.45). PK(quick_link_id)는 BaseVO.rowId. */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuickLinkVO extends BaseVO {

    private String name;
    /** 이동 주소 — '/'로 시작하는 사이트 안 경로 또는 http(s):// (QuickLinkService가 검사) */
    private String linkUrl;
    /** 아이콘 키(프론트 MenuGlyph, 메뉴 아이콘과 같은 목록) */
    private String icon;
    /** 새 창으로 열기 */
    private String targetYn;
    /** 메인 노출 — N이면 메인에서만 빠지고 순서 자리는 지킨다 */
    private String displayYn;
    private String sortNo;
}
