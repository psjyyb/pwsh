import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

/** 공통 404 페이지 — 존재하지 않는 경로. 홈(/gen)으로 이동 제공. */
export default function NotFound() {
  const navigate = useNavigate()
  return (
    <Result
      status="404"
      title="404"
      subTitle="요청하신 페이지를 찾을 수 없습니다."
      extra={
        <Button type="primary" onClick={() => navigate('/gen')}>
          홈으로
        </Button>
      }
    />
  )
}
