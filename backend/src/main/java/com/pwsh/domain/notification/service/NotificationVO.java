package com.pwsh.domain.notification.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 인앱 알림 VO (t_notification). PK(noti_id)는 BaseVO.dbKey. 수신자=user_id(서버 세팅). */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationVO extends BaseVO {

    private String userId;   // 수신자(서버에서 세팅)
    private String notiType; // APPLY/ACCEPT/REJECT/COMMENT
    private String content;  // 표시 문구
    private String linkUrl;  // 클릭 시 이동 경로
    private String readYn;   // 읽음 여부
}
