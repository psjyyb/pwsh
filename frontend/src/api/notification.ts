import { apiPost } from './http'

/** 인앱 알림 */
export interface Noti {
  rowId?: string
  type?: string // APPLY/ACCEPT/REJECT/COMMENT
  content?: string
  linkUrl?: string
  readYn?: string
  regDt?: string
}

/** 알림 수신 설정(유형별 Y/N. 미설정이면 전부 Y). */
export interface NotificationSetting {
  applyYn?: string   // 모집 신청/수락/거절
  commentYn?: string // 댓글·답글
  messageYn?: string // 쪽지
  reviewYn?: string  // 후기
}

export const notificationApi = {
  /** 내 알림 최근 목록 */
  list: () => apiPost<Noti[]>('/adm/notification/selectNotificationList.do', {}),
  /** 내 알림 수신 설정 조회 */
  setting: () => apiPost<NotificationSetting>('/adm/notification/selectNotificationListSetting.do', {}),
  /** 내 알림 수신 설정 저장 */
  saveSetting: (s: NotificationSetting) => apiPost<void>('/adm/notification/updateNotificationSetting.do', s),
  /** 내 미읽음 수(헤더 배지) */
  unreadCnt: () => apiPost<number>('/adm/notification/selectNotificationListUnreadCnt.do', {}),
  /** 단건 읽음 */
  read: (rowId: string) => apiPost<void>('/adm/notification/updateNotificationRead.do', { rowId }),
  /** 전체 읽음 */
  readAll: () => apiPost<void>('/adm/notification/updateNotificationReadAll.do', {}),
}
