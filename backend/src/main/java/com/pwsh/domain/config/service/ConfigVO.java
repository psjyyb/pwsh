package com.pwsh.domain.config.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 환경설정 VO (config, 단일 행). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigVO extends BaseVO {

    private String failCntLimit;
    private String failLockMins;
    private String passwordExpireDays;
    private String sessionExpireMins;
    private String delLogDays;
    /** 미접속 휴면 전환일(0이면 휴면 전환 안 함) */
    private String dormantDays;
    /** 휴면 전환 며칠 전 안내메일(0이면 안 보냄) */
    private String dormantNotifyDays;
    /** 탈퇴 후 개인정보 보존일(0이면 즉시 파기) */
    private String destroyDays;
    private String accIpYn;
    private String maintYn;
    private String maintMessage;
    private String title;
    private String menuVersion;
    private String logoFileId; // 관리자 로고 파일(file_ref map_key=config_id, loc='LOGO'), 조회 전용
}
