package com.pwsh.domain.authgroup.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 권한그룹 VO (auth_group). menuIds/menuId는 그룹-메뉴 권한(auth) 저장용. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthGroupVO extends BaseVO {

    private String authGroupId;
    private String authGroupName;
    private String description;

    private String[] menuIds; // 권한설정 저장 시 선택된 메뉴 ID 목록
    private String menuId;     // 개별 auth insert용

    private String[] memberIds; // 그룹-사용자 지정 저장 시 선택된 사용자 목록
    private String memberId;     // 개별 auth_member insert용
}
