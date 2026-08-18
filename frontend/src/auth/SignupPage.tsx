import { useEffect, useState } from 'react'
import { Button, Card, ConfigProvider, Form, Input, Space, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { signup, login, sendSignupCode } from '../api/auth'
import { tokenStore } from './token'
import { configApi } from '../adm/config/config.api'
import { genTheme } from '../gen/theme'
import defaultLogo from '../assets/logo.svg'

/**
 * 셀프 회원가입 — 공개 페이지(비로그인). 아이디·비번·닉네임 필수, 이메일 선택.
 * 성공 시 로그인 페이지로 이동(자동 로그인 아님).
 */
export default function SignupPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const [submitting, setSubmitting] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [codeSent, setCodeSent] = useState(false)
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo

  // 가입 이메일 인증코드 발송 — 이메일 필드 검증 후 요청
  const handleSendCode = async () => {
    try {
      await form.validateFields(['email'])
    } catch {
      return // 이메일 형식/필수 오류는 폼이 표시
    }
    const email = form.getFieldValue('email') as string
    setSendingCode(true)
    try {
      await sendSignupCode(email)
      setCodeSent(true)
      message.success('인증코드를 메일로 보냈습니다. (유효 10분)')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '인증코드 발송에 실패했습니다.')
    } finally {
      setSendingCode(false)
    }
  }

  useEffect(() => {
    configApi.view().then((c) => {
      if (c.title) setSiteTitle(c.title)
      setLogoFileId(c.logoFileId ?? undefined)
    }).catch(() => {})
  }, [])

  const onFinish = async (values: {
    userId: string; userPw: string; pwConfirm: string; nickname: string; email: string; code: string
  }) => {
    setSubmitting(true)
    try {
      await signup({
        userId: values.userId,
        userPw: values.userPw,
        pwConfirm: values.pwConfirm,
        nickname: values.nickname,
        email: values.email,
        code: values.code,
      })
      // 가입 성공 → 바로 로그인 처리(재로그인 불필요) 후 관심 취미 고르기로.
      // 취미를 하나도 안 담으면 피드가 비고 새 모집 알림도 안 오므로, 첫 화면에서 고르게 한다(건너뛸 수 있음).
      const token = await login(values.userId, values.userPw)
      tokenStore.set(token.accessToken, token.refreshToken)
      message.success('환영합니다! 회원가입이 완료되었습니다.')
      navigate('/gen/onboarding', { replace: true })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '회원가입에 실패했습니다.')
    } finally {
      setSubmitting(false)
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
      <Card style={{ width: 420, boxShadow: '0 20px 50px rgba(43,32,87,.4)', borderRadius: 24 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <img
            src={logoSrc}
            alt={siteTitle}
            style={{ width: 300, height: 56, objectFit: 'contain', marginBottom: 6 }}
          />
          <div style={{ color: '#888', fontSize: 13 }}>함께 시작해요 ✨ 회원가입</div>
        </div>

        <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            name="userId"
            label="아이디"
            rules={[{ required: true, message: '아이디를 입력하세요.' }]}
          >
            <Input autoFocus placeholder="로그인에 사용할 아이디" autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="닉네임"
            rules={[{ required: true, message: '닉네임을 입력하세요.' }]}
            extra="게시글·모집에 표시되는 이름입니다."
          >
            <Input placeholder="닉네임" maxLength={30} />
          </Form.Item>
          <Form.Item
            name="userPw"
            label="비밀번호"
            rules={[
              { required: true, message: '비밀번호를 입력하세요.' },
              { min: 8, message: '비밀번호는 8자 이상이어야 합니다.' },
            ]}
            extra="8~64자, 영문·숫자·특수문자를 모두 포함."
          >
            <Input.Password placeholder="비밀번호" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="pwConfirm"
            label="비밀번호 확인"
            dependencies={['userPw']}
            rules={[
              { required: true, message: '비밀번호를 한 번 더 입력하세요.' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('userPw') === value) return Promise.resolve()
                  return Promise.reject(new Error('비밀번호가 일치하지 않습니다.'))
                },
              }),
            ]}
          >
            <Input.Password placeholder="비밀번호 확인" autoComplete="new-password" />
          </Form.Item>
          <Form.Item label="이메일" required extra="인증코드를 받을 이메일입니다.">
            <Space.Compact style={{ width: '100%' }}>
              <Form.Item
                name="email"
                noStyle
                rules={[
                  { required: true, message: '이메일을 입력하세요.' },
                  { type: 'email', message: '이메일 형식이 올바르지 않습니다.' },
                ]}
              >
                <Input placeholder="example@email.com" autoComplete="email" />
              </Form.Item>
              <Button
                type="primary" ghost                onClick={handleSendCode} loading={sendingCode}
                style={{ flex: '0 0 auto', fontSize: 14, fontWeight: 500, whiteSpace: 'nowrap', paddingInline: 18 }}
              >
                {codeSent ? '재전송' : '인증코드 받기'}
              </Button>
            </Space.Compact>
          </Form.Item>
          <Form.Item
            name="code"
            label="인증코드"
            rules={[{ required: true, message: '메일로 받은 인증코드를 입력하세요.' }]}
            extra="이메일로 전송된 6자리 코드를 입력하세요. (유효 10분)"
          >
            <Input placeholder="6자리 인증코드" maxLength={6} inputMode="numeric" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            가입하기
          </Button>
          <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13 }}>
            이미 계정이 있으신가요?{' '}
            <a onClick={() => navigate('/login')}>로그인</a>
          </div>
        </Form>
      </Card>
    </div>
    </ConfigProvider>
  )
}
