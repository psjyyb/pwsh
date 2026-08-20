package com.pwsh.domain.memberhobby.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 회원별 취미 레벨 VO (member_hobby). 회원=본인(서버 세팅). */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberHobbyVO extends BaseVO {

    private String memberId;    // 회원(서버에서 세팅)
    private String hobbyId;   // 취미
    private String levelCd;   // 레벨(HOBBYLV00)
    // 조회 표시용(조인)
    private String hobbyName;
    private String levelName;
}
