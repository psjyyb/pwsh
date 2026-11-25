package com.pwsh.domain.seo.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RSS · sitemap(CMS 1.2.43) 조회 한 줄 — 게시판·게시글·메뉴·취미·모집을 같은 VO로 받는다(표가 따로 없는 조회 전용 도메인).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SeoVO extends BaseVO {

    private String boardId;
    private String boardName;
    private String description;    // 게시판 설명(RSS 채널 설명)
    private String title;
    private String content;        // 본문 HTML — RSS 요약은 서비스가 태그를 지워 만든다
    private String regName;        // 작성자 닉네임(사용자 화면에 이미 공개된 값)
    private String connCd;         // 메뉴 연결유형(MENU01 주소 / 02 게시판 / 03 페이지 / 05 폼)
    private String connId;
    private String linkUrl;
}
