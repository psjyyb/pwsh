package com.pwsh.domain.banword.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 금칙어 VO (banword). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class BanwordVO extends BaseVO {

    // PK(banword_id)는 BaseVO.rowId로 통일
    private String word;
}
