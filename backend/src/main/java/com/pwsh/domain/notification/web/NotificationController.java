package com.pwsh.domain.notification.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.notification.service.NotificationService;
import com.pwsh.domain.notification.service.NotificationVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인앱 알림 — 본인 것만(로그인 필요). 로직은 {@link NotificationService}.
 */
@RestController
@RequestMapping("/api/adm/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @RequestMapping("/selectNotificationList.do")
    public ApiResponse<List<NotificationVO>> selectList() {
        return ApiResponse.ok(notificationService.selectMyList());
    }

    @RequestMapping("/selectNotificationListUnreadCnt.do")
    public ApiResponse<Integer> unreadCnt() {
        return ApiResponse.ok(notificationService.unreadCount());
    }

    @RequestMapping("/updateNotificationRead.do")
    public ApiResponse<Void> read(@RequestBody NotificationVO vo) {
        Validate.required(vo.getRowId(), "알림");
        notificationService.markRead(vo.getRowId());
        return ApiResponse.ok();
    }

    @RequestMapping("/updateNotificationReadAll.do")
    public ApiResponse<Void> readAll() {
        notificationService.markAllRead();
        return ApiResponse.ok();
    }

    /** 내 알림 수신 설정 조회(유형별 Y/N. 미설정이면 전부 Y) */
    @RequestMapping("/selectNotificationListSetting.do")
    public ApiResponse<NotificationVO> selectSetting() {
        return ApiResponse.ok(notificationService.selectMySetting());
    }

    /** 내 알림 수신 설정 저장 */
    @RequestMapping("/updateNotificationSetting.do")
    public ApiResponse<Void> updateSetting(@RequestBody NotificationVO vo) {
        notificationService.saveMySetting(vo);
        return ApiResponse.ok();
    }
}
