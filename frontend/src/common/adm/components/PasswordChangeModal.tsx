import { Form, Input, Modal, message } from 'antd'
import { changePw } from '../../../api/auth'

interface Props {
  open: boolean
  onClose: () => void
}

/** 본인 비밀번호 변경 (현재 비번 검증 + 새 비번 확인). 헤더 버튼/만료 알림에서 호출. */
export default function PasswordChangeModal({ open, onClose }: Props) {
  const [form] = Form.useForm()

  const submit = async () => {
    const v = await form.validateFields()
    try {
      await changePw(v.currentPw, v.newPw)
      message.success('비밀번호가 변경되었습니다.')
      form.resetFields()
      onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '비밀번호 변경에 실패했습니다.')
    }
  }

  return (
    <Modal
      open={open}
      title="비밀번호 변경"
      onOk={submit}
      onCancel={() => {
        form.resetFields()
        onClose()
      }}
      okText="변경"
      cancelText="취소"
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item name="currentPw" label="현재 비밀번호" rules={[{ required: true, message: '현재 비밀번호를 입력하세요.' }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item name="newPw" label="새 비밀번호" rules={[{ required: true, message: '새 비밀번호를 입력하세요.' }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPw"
          label="새 비밀번호 확인"
          dependencies={['newPw']}
          rules={[
            { required: true, message: '새 비밀번호를 다시 입력하세요.' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPw') === value) return Promise.resolve()
                return Promise.reject(new Error('새 비밀번호가 일치하지 않습니다.'))
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
