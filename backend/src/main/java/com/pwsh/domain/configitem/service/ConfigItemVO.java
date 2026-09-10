package com.pwsh.domain.configitem.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 확장 설정 VO (config_item). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigItemVO extends BaseVO {

    // PK는 문자열 키다 — 자동증가 PK가 아니라 BaseVO.rowId에 config_key를 담는다.
    private String configKey;
    private String value;
    private String inputType;
    private String name;
    private String description;
    private String groupCd;
    private String sortNo;
    /** 비로그인 공개 여부. 관리자 화면이 '공개' 표시를 띄우는 근거 */
    private String publicYn;
}
