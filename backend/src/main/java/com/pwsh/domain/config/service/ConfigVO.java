package com.pwsh.domain.config.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 환경설정 VO (t_config, 단일 행). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigVO extends BaseVO {

    private String failCntLimit;
    private String failCntDeniedTi;
    private String pwExpireCnt;
    private String sessionExpireCnt;
    private String delLogCnt;
    private String accIpYn;
    private String title;
    private String menuVersion;
    private String logoFileId; // 관리자 로고 파일(r_file map_key=config_id, loc='LOGO'), 조회 전용
}
