package com.pwsh.domain.userhobby.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 회원별 취미 레벨 VO (t_user_hobby). 회원=본인(서버 세팅). */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserHobbyVO extends BaseVO {

    private String userId;    // 회원(서버에서 세팅)
    private String hobbyId;   // 취미
    private String levelCd;   // 레벨(HOBBYLV00)
    // 조회 표시용(조인)
    private String hobbyNm;
    private String levelNm;
}
