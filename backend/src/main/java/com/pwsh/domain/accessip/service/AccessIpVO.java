package com.pwsh.domain.accessip.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 관리자 접속 허용 IP VO (access_ip). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccessIpVO extends BaseVO {

    // PK(access_ip_id)는 BaseVO.rowId로 통일
    private String ip;
    private String description;
}
