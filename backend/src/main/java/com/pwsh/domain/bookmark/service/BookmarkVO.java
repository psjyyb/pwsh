package com.pwsh.domain.bookmark.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/** 북마크(bookmark). member_id는 서버 강제, 토글 방식. */
@Getter
@Setter
public class BookmarkVO extends BaseVO {

    private String memberId;
    private String targetType; // POST/RECRUIT
    private String targetId;

    // 조회 표시용
    private String title;       // 대상 제목
    private String subName;       // 게시판명(POST) / 취미명(RECRUIT)
    private String boardId;   // POST: 이동 링크용
    private String statusName;    // RECRUIT: 상태명
    private String markedYn;    // 토글 결과(Y=북마크됨)
}
