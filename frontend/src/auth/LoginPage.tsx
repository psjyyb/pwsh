import { useEffect, useState } from 'react'
import { Button, Card, ConfigProvider, Form, Input, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { configApi } from '../adm/config/config.api'
import { tokenStore, isAdmin } from './token'
import { genTheme } from '../gen/theme'
import defaultLogo from '../assets/logo.svg'

export default function LoginPage() {
  const navigate = useNavigate()
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo

  useEffect(() => {
    configApi.view().then((c) => {
      if (c.title) setSiteTitle(c.title)
      setLogoFileId(c.logoFileId ?? undefined)
    }).catch(() => {})
  }, [])

  const onFinish = async (values: { userId: string; userPw: string }) => {
    try {
      const token = await login(values.userId, values.userPw)
      tokenStore.set(token.accessToken, token.refreshToken)
      // 비밀번호 만료 알림용 플래그 저장(레이아웃 진입 시 1회 표시 후 소거)
      if (token.pwExpired) {
        sessionStorage.setItem('pwExpired', 'Y')
      }
      // 관리자(MEM02)는 관리자 메인, 그 외 일반 사용자는 사용자 영역으로
      navigate(isAdmin() ? '/adm/dashboard' : '/gen', { replace: true })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '로그인에 실패했습니다.')
    }
  }

  return (
    <ConfigProvider theme={genTheme}>
    <div
      style={{
        display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh',
        background: 'linear-gradient(160deg, #8B72F5 0%, #B9A6F7 100%)', padding: 16,
      }}
    >
      <Card style={{ width: 380, boxShadow: '0 20px 50px rgba(43,32,87,.4)', borderRadius: 24 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <img
            src={logoSrc}
            alt={siteTitle}
            style={{ width: 300, height: 56, objectFit: 'contain', marginBottom: 6 }}
          />
          <div style={{ color: '#888', fontSize: 13 }}>반가워요 💜 로그인</div>
        </div>

        <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="userId" label="아이디" rules={[{ required: true, message: '아이디를 입력하세요.' }]}>
            <Input autoFocus placeholder="아이디" />
          </Form.Item>
          <Form.Item name="userPw" label="비밀번호" rules={[{ required: true, message: '비밀번호를 입력하세요.' }]}>
            <Input.Password placeholder="비밀번호" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            로그인
          </Button>
          <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13 }}>
            아직 회원이 아니신가요?{' '}
            <a onClick={() => navigate('/signup')}>회원가입</a>
            <span style={{ color: '#ddd', margin: '0 8px' }}>|</span>
            <a onClick={() => navigate('/forgot')}>비밀번호 찾기</a>
          </div>
        </Form>
      </Card>
    </div>
    </ConfigProvider>
  )
}
