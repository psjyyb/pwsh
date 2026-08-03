import { useEffect, useState } from 'react'
import { Button, Card, ConfigProvider, Form, Input, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { signup, login } from '../api/auth'
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
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const [submitting, setSubmitting] = useState(false)
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo

  useEffect(() => {
    configApi.view().then((c) => {
      if (c.title) setSiteTitle(c.title)
      setLogoFileId(c.logoFileId ?? undefined)
    }).catch(() => {})
  }, [])

  const onFinish = async (values: {
    userId: string; userPw: string; pwConfirm: string; nickname: string; email?: string
  }) => {
    setSubmitting(true)
    try {
      await signup({
        userId: values.userId,
        userPw: values.userPw,
        pwConfirm: values.pwConfirm,
        nickname: values.nickname,
        email: values.email,
      })
      // 가입 성공 → 바로 로그인 처리 후 메인으로(재로그인 불필요)
      const token = await login(values.userId, values.userPw)
      tokenStore.set(token.accessToken, token.refreshToken)
      message.success('환영합니다! 회원가입이 완료되었습니다.')
      navigate('/gen', { replace: true })
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

        <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            name="userId"
            label="아이디"
            rules={[{ required: true, message: '아이디를 입력하세요.' }]}
          >
            <Input size="large" autoFocus placeholder="로그인에 사용할 아이디" autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="닉네임"
            rules={[{ required: true, message: '닉네임을 입력하세요.' }]}
            extra="게시글·모집에 표시되는 이름입니다."
          >
            <Input size="large" placeholder="닉네임" maxLength={30} />
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
            <Input.Password size="large" placeholder="비밀번호" autoComplete="new-password" />
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
            <Input.Password size="large" placeholder="비밀번호 확인" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="email"
            label="이메일 (선택)"
            rules={[{ type: 'email', message: '이메일 형식이 올바르지 않습니다.' }]}
          >
            <Input size="large" placeholder="example@email.com" autoComplete="email" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
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
