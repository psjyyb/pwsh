import { Button, Result } from 'antd'
import { MAINT_MESSAGE_KEY } from '../api/client'

/**
 * 점검(유지보수) 안내 화면.
 * 서버가 503(C503)을 주면 axios 인터셉터가 문구를 저장하고 이 경로로 보낸다.
 * 관리자는 애초에 503을 받지 않으므로 이 화면을 보지 않는다.
 */
export default function Maintenance() {
  const message =
    sessionStorage.getItem(MAINT_MESSAGE_KEY) || '서비스 점검 중입니다. 잠시 후 다시 이용해 주세요.'
  return (
    <Result
      status="warning"
      title="서비스 점검 중"
      subTitle={message}
      extra={
        <Button type="primary" onClick={() => window.location.assign('/gen')}>
          다시 시도
        </Button>
      }
    />
  )
}
