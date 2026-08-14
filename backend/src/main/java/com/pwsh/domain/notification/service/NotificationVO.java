package com.pwsh.domain.notification.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 인앱 알림 VO (t_notification). PK(noti_id)는 BaseVO.rowId. 수신자=user_id(서버 세팅). */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationVO extends BaseVO {

    private String userId;   // 수신자(서버에서 세팅)
    private String notiType; // APPLY/ACCEPT/REJECT/COMMENT
    private String content;  // 표시 문구
    private String linkUrl;  // 클릭 시 이동 경로
    private String readYn;   // 읽음 여부

    // ===== 알림 수신 설정(t_noti_setting). 행 없으면 전부 'Y'(기본 수신) =====
    private String notiApply;   // 모집 신청/수락/거절
    private String notiComment; // 댓글·답글
    private String notiMessage; // 쪽지
    private String notiReview;  // 후기
}
