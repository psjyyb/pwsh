import { apiPost } from './http'

/** 대화 목록 항목(상대별 최근 메시지 + 안읽음). */
export interface Conversation {
  rowId?: string       // = 상대 handle
  otherHandle?: string // 상대 공개 식별자(대화 키)
  otherName?: string
  otherFileId?: string
  lastContent?: string
  lastDt?: string
  unreadCnt?: string
  lastMine?: string // 최근 메시지가 내가 보낸 것인지(Y/N)
}

/** 대화 스레드의 개별 쪽지. 발신/수신 로그인 ID는 내려오지 않고 mine으로 구분. */
export interface Message {
  rowId?: string
  content?: string
  readYn?: string
  regDt?: string
  mine?: string // 내가 보낸 것인지(Y/N)
}

export const messageApi = {
  /** 내 대화 목록 */
  convList: () => apiPost<Conversation[]>('/adm/message/selectMessageList.do', {}),
  /** 특정 상대(handle)와의 대화(열람 시 읽음처리) */
  thread: (otherHandle: string) => apiPost<Message[]>('/adm/message/selectMessageListThread.do', { otherHandle }),
  /** 전체 안읽음 수(헤더 배지) */
  unreadCnt: () => apiPost<number>('/adm/message/selectMessageListUnreadCnt.do', {}),
  /** 쪽지 보내기(받는 사람은 handle) */
  send: (receiverHandle: string, content: string) =>
    apiPost<void>('/adm/message/insertMessage.do', { receiverHandle, content }),
  /** 상대와의 대화 읽음 처리 */
  markRead: (otherHandle: string) => apiPost<void>('/adm/message/updateMessageRead.do', { otherHandle }),
  /** 대화 삭제(내 화면에서만) */
  removeConv: (otherHandle: string) => apiPost<void>('/adm/message/deleteMessage.do', { otherHandle }),
}
