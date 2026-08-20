package com.pwsh.domain.notification.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 인앱 알림 VO (notification). PK(notification_id)는 BaseVO.rowId. 수신자=member_id(서버 세팅). */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationVO extends BaseVO {

    private String memberId;   // 수신자(서버에서 세팅)
    private String type; // APPLY/ACCEPT/REJECT/COMMENT
    private String content;  // 표시 문구
    private String linkUrl;  // 클릭 시 이동 경로
    private String readYn;   // 읽음 여부
    private String nickname; // 멘션 대상 조회 결과(@닉네임 → 회원) 표시용

    // ===== 알림 수신 설정(notification_setting). 행 없으면 전부 'Y'(기본 수신) =====
    private String applyYn;   // 모집 신청/수락/거절
    private String commentYn; // 댓글·답글
    private String messageYn; // 쪽지
    private String reviewYn;  // 후기
}
