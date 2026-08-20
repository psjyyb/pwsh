import { useEffect, useState } from 'react'
import { Button, Card, ConfigProvider, Form, Input, Space, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { sendResetCode, resetPassword } from '../api/auth'
import { configApi } from '../adm/config/config.api'
import { genTheme } from '../gen/theme'
import defaultLogo from '../assets/logo.svg'

/**
 * 비밀번호 찾기(재설정) — 공개 페이지(비로그인).
 * 아이디로 인증코드 발송 → 등록된 이메일로 받은 6자리 코드 + 새 비밀번호로 재설정.
 * (계정/이메일 존재 여부는 서버가 노출하지 않음 — 항상 "발송했습니다" 응답)
 */
export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const [sendingCode, setSendingCode] = useState(false)
  const [codeSent, setCodeSent] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo

  useEffect(() => {
    configApi.view().then((c) => {
      if (c.title) setSiteTitle(c.title)
      setLogoFileId(c.logoFileId ?? undefined)
    }).catch(() => {})
  }, [])

  const handleSendCode = async () => {
    try {
      await form.validateFields(['memberId'])
    } catch {
      return
    }
    const memberId = form.getFieldValue('memberId') as string
    setSendingCode(true)
    try {
      await sendResetCode(memberId)
      setCodeSent(true)
      // 열거 방지: 계정 유무와 무관하게 동일 안내
      message.success('가입 시 등록한 이메일로 인증코드를 보냈습니다. (계정이 있는 경우, 유효 10분)')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '인증코드 발송에 실패했습니다.')
    } finally {
      setSendingCode(false)
    }
  }

  const onFinish = async (values: {
    memberId: string; code: string; newPw: string; pwConfirm: string
  }) => {
    setSubmitting(true)
    try {
      await resetPassword(values)
      message.success('비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해 주세요.')
      navigate('/login', { replace: true })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '비밀번호 재설정에 실패했습니다.')
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
          <div style={{ color: '#888', fontSize: 13 }}>비밀번호 찾기 🔑</div>
        </div>

        <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item label="아이디" required extra="가입 시 등록한 이메일로 인증코드를 보냅니다.">
            <Space.Compact style={{ width: '100%' }}>
              <Form.Item name="memberId" noStyle rules={[{ required: true, message: '아이디를 입력하세요.' }]}>
                <Input autoFocus placeholder="아이디" autoComplete="username" />
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
          <Form.Item
            name="newPw"
            label="새 비밀번호"
            rules={[
              { required: true, message: '새 비밀번호를 입력하세요.' },
              { min: 8, message: '비밀번호는 8자 이상이어야 합니다.' },
            ]}
            extra="8~64자, 영문·숫자·특수문자를 모두 포함."
          >
            <Input.Password placeholder="새 비밀번호" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="pwConfirm"
            label="새 비밀번호 확인"
            dependencies={['newPw']}
            rules={[
              { required: true, message: '새 비밀번호를 한 번 더 입력하세요.' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPw') === value) return Promise.resolve()
                  return Promise.reject(new Error('비밀번호가 일치하지 않습니다.'))
                },
              }),
            ]}
          >
            <Input.Password placeholder="새 비밀번호 확인" autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            비밀번호 재설정
          </Button>
          <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13 }}>
            <a onClick={() => navigate('/login')}>로그인으로 돌아가기</a>
          </div>
        </Form>
      </Card>
    </div>
    </ConfigProvider>
  )
}
