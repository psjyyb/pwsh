import { apiPost } from './http'

/** 대화 목록 항목(상대별 최근 메시지 + 안읽음). */
export interface Conversation {
  dbKey?: string
  otherId?: string
  otherNm?: string
  otherFileId?: string
  lastContent?: string
  lastDt?: string
  unreadCnt?: string
  lastMine?: string // 최근 메시지가 내가 보낸 것인지(Y/N)
}

/** 대화 스레드의 개별 쪽지. */
export interface Message {
  dbKey?: string
  senderId?: string
  receiverId?: string
  content?: string
  readYn?: string
  regDt?: string
  mine?: string // 내가 보낸 것인지(Y/N)
}

export const messageApi = {
  /** 내 대화 목록 */
  convList: () => apiPost<Conversation[]>('/adm/message/selectMessageList.do', {}),
  /** 특정 상대와의 대화(열람 시 읽음처리) */
  thread: (otherId: string) => apiPost<Message[]>('/adm/message/selectMessageListThread.do', { otherId }),
  /** 전체 안읽음 수(헤더 배지) */
  unreadCnt: () => apiPost<number>('/adm/message/selectMessageListUnreadCnt.do', {}),
  /** 쪽지 보내기 */
  send: (receiverId: string, content: string) =>
    apiPost<void>('/adm/message/insertMessage.do', { receiverId, content }),
  /** 상대와의 대화 읽음 처리 */
  markRead: (otherId: string) => apiPost<void>('/adm/message/updateMessageRead.do', { otherId }),
  /** 대화 삭제(내 화면에서만) */
  removeConv: (otherId: string) => apiPost<void>('/adm/message/deleteMessage.do', { otherId }),
}
