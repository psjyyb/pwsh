package com.pwsh.domain.bookmark.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/** 북마크(t_bookmark). user_id는 서버 강제, 토글 방식. */
@Getter
@Setter
public class BookmarkVO extends BaseVO {

    private String userId;
    private String targetType; // BBS/RECRUIT
    private String targetId;

    // 조회 표시용
    private String title;       // 대상 제목
    private String subNm;       // 게시판명(BBS) / 취미명(RECRUIT)
    private String bbsinfoId;   // BBS: 이동 링크용
    private String statusNm;    // RECRUIT: 상태명
    private String markedYn;    // 토글 결과(Y=북마크됨)
}
