import { useEffect, useState } from 'react'
import { Alert, Button, Card, ConfigProvider, Form, Input, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { login, loginOtp } from '../api/auth'
import type { TokenResponse } from '../api/auth'
import { configApi } from '../adm/config/config.api'
import { pubConfigItemApi } from '../adm/configitem/configitem.api'
import { tokenStore, isAdmin } from './token'
import { genTheme } from '../gen/theme'
import defaultLogo from '../assets/logo.svg'

export default function LoginPage() {
  const navigate = useNavigate()
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const [notice, setNotice] = useState('')
  // 관리자 2차 인증 단계 — 비밀번호를 통과하면 서버가 토큰 대신 확인 키를 준다
  const [otp, setOtp] = useState<{ key: string; mailSent: boolean } | null>(null)
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo

  useEffect(() => {
    configApi.view().then((c) => {
      if (c.title) setSiteTitle(c.title)
      setLogoFileId(c.logoFileId ?? undefined)
    }).catch(() => {})
    // 확장설정 site.login-notice — 점검 예고·공지 등을 로그인 화면에 띄운다(비어 있으면 미표시)
    pubConfigItemApi.list().then((rows) => {
      const found = rows.find((r) => r.configKey === 'site.login-notice')
      if (found?.value?.trim()) setNotice(found.value)
    }).catch(() => {})
  }, [])

  const enter = (token: TokenResponse) => {
    tokenStore.set(token.accessToken, token.refreshToken)
    // 비밀번호 만료 알림용 플래그 저장(레이아웃 진입 시 1회 표시 후 소거)
    if (token.pwExpired) {
      sessionStorage.setItem('pwExpired', 'Y')
    }
    // 관리자(MEM02)는 관리자 메인, 그 외 일반 사용자는 사용자 영역으로
    navigate(isAdmin() ? '/adm/dashboard' : '/gen', { replace: true })
  }

  const onFinish = async (values: { memberId: string; password: string }) => {
    try {
      const token = await login(values.memberId, values.password)
      if (token.otpKey) {
        setOtp({ key: token.otpKey, mailSent: token.otpMailSent !== false })
        return
      }
      enter(token)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '로그인에 실패했습니다.')
    }
  }

  const onOtp = async (values: { code: string }) => {
    try {
      enter(await loginOtp(otp!.key, values.code.trim()))
    } catch (e) {
      // 만료·5회 초과면 이 키로는 더 안 된다 — 메시지를 보고 [처음으로]를 눌러 다시 로그인한다
      message.error(e instanceof Error ? e.message : '인증에 실패했습니다.')
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

        {/* 확장설정 site.login-notice — 비어 있으면 렌더하지 않는다 */}
        {notice && (
          <Alert type="info" showIcon style={{ marginBottom: 16, whiteSpace: 'pre-line' }} message={notice} />
        )}

        {otp ? (
          <Form layout="vertical" onFinish={onOtp} requiredMark={false}>
            {otp.mailSent ? (
              <Alert type="success" showIcon style={{ marginBottom: 16 }}
                message="등록된 이메일로 6자리 인증 코드를 보냈습니다." description="5분 안에 입력하세요. 다시 받으려면 처음부터 로그인하세요." />
            ) : (
              <Alert type="warning" showIcon style={{ marginBottom: 16 }}
                message="인증 코드 메일을 보내지 못했습니다." description="메일 발송 설정을 확인해야 합니다. 다른 관리자에게 문의하세요." />
            )}
            <Form.Item
              name="code"
              label="인증 코드"
              rules={[{ required: true, pattern: /^\s*\d{6}\s*$/, message: '6자리 숫자를 입력하세요.' }]}
            >
              <Input autoFocus inputMode="numeric" maxLength={6} placeholder="000000" autoComplete="one-time-code" />
            </Form.Item>
            <Button type="primary" htmlType="submit" block>
              확인
            </Button>
            <Button type="link" block style={{ marginTop: 8 }} onClick={() => setOtp(null)}>
              처음으로
            </Button>
          </Form>
        ) : (
          <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
            <Form.Item name="memberId" label="아이디" rules={[{ required: true, message: '아이디를 입력하세요.' }]}>
              <Input autoFocus placeholder="아이디" />
            </Form.Item>
            <Form.Item name="password" label="비밀번호" rules={[{ required: true, message: '비밀번호를 입력하세요.' }]}>
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
        )}
      </Card>
    </div>
    </ConfigProvider>
  )
}
