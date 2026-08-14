package com.pwsh.domain.menu.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 메뉴 VO (t_menu). BaseVO 상속. */
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuVO extends BaseVO {

    // 자기 PK(menu_id)는 BaseVO.rowId로 통일. p_menu_id는 부모 참조라 명시 필드 유지.
    private String pMenuId;
    private String area;      // ADM=관리자, GEN=사용자
    private String menuNm;
    private String menuDesc;
    private String ordr;
    private String connTy;    // 연결유형 t_code MENU00: MENU01=URL, MENU02=게시판, MENU03=페이지, MENU04=그룹
    private String connId;    // 게시판/페이지 대상 ID (MENU02/MENU03)
    private String linkUrl;   // 라우트/URL (MENU01)
    private String targetYn;
    private String icon;      // 메뉴 아이콘 키(프론트 아이콘 레지스트리 매핑)

    // 트리 권한 필터용 (컨트롤러가 세팅)
    private String userId;   // 로그인 사용자
    private String superYn;  // 슈퍼관리자면 'Y' → 전체 노출
}
