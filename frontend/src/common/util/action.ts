import { message } from 'antd'

/**
 * 비동기 작업 + 성공/실패 메시지 공통 처리 (등록/수정/삭제 등에서 재사용).
 *   runWithMessage(() => api.remove(id), '삭제되었습니다.', reload)
 */
export async function runWithMessage(
  fn: () => Promise<unknown>,
  successMsg: string,
  onDone?: () => void,
): Promise<void> {
  try {
    await fn()
    message.success(successMsg)
    onDone?.()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '처리에 실패했습니다.')
  }
}
