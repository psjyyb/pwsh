import { apiPost } from './http'

/** 인앱 알림 */
export interface Noti {
  dbKey?: string
  notiType?: string // APPLY/ACCEPT/REJECT/COMMENT
  content?: string
  linkUrl?: string
  readYn?: string
  regDt?: string
}

export const notificationApi = {
  /** 내 알림 최근 목록 */
  list: () => apiPost<Noti[]>('/adm/notification/selectNotificationList.do', {}),
  /** 내 미읽음 수(헤더 배지) */
  unreadCnt: () => apiPost<number>('/adm/notification/selectNotificationListUnreadCnt.do', {}),
  /** 단건 읽음 */
  read: (dbKey: string) => apiPost<void>('/adm/notification/updateNotificationRead.do', { dbKey }),
  /** 전체 읽음 */
  readAll: () => apiPost<void>('/adm/notification/updateNotificationReadAll.do', {}),
}
