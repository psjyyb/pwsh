package com.pwsh.domain.code.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 공통코드 VO (t_code). BaseVO 상속(페이징/검색/audit 공통). 필드 String 통일.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeVO extends BaseVO {

    private String codeId;
    private String pCodeId;
    private String codeNm;
    private String codeDesc;
    private String sortNo;
}
