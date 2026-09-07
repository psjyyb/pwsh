import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Select, message } from 'antd'
import NumberInput from '../../common/adm/components/NumberInput'
import ImageUpload from '../../common/adm/components/ImageUpload'
import { fileApi } from '../../api/file'
import { configApi } from './config.api'
import type { Config } from './config.api'

const LOGO_MAP_KEY = '1' // config 단일행(config_id=1)에 로고 매핑

/** DB에 Y/N 한 글자로 저장되는 항목의 선택지 */
const YN_OPTIONS = [
  { value: 'Y', label: '사용' },
  { value: 'N', label: '사용안함' },
]

/** 환경설정 (단일 행 — 조회 후 수정) */
export default function ConfigPage() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    configApi
      .view()
      .then((cfg) => form.setFieldsValue(cfg))
      .catch((e) => message.error(e instanceof Error ? e.message : '조회에 실패했습니다.'))
  }, [form])

  const onFinish = async (values: Config) => {
    setLoading(true)
    try {
      await configApi.update(values)
      // 로고: config 단일행에 LOGO 매핑 저장(없으면 매핑 제거 → 기본 로고로 폴백)
      await fileApi.saveMapping(LOGO_MAP_KEY, 'LOGO', values.logoFileId ? [values.logoFileId] : [])
      message.success('저장되었습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card title="환경설정">
      <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 520 }}>
        <Form.Item name="title" label="사이트 타이틀">
          <Input />
        </Form.Item>
        <Form.Item
          name="logoFileId"
          label="로고 이미지"
          extra="관리자 상단/사이드바에 표시됩니다. 가로형 권장(높이 기준으로 자동 축소, 비율 유지). 미설정 시 기본 로고."
        >
          <ImageUpload />
        </Form.Item>
        <Form.Item
          name="accIpYn"
          label="접속 IP 제한"
          extra="켜면 [접속IP관리]에 등록된 IP에서만 관리자 기능을 쓸 수 있습니다. 목록이 비어 있으면 제한은 동작하지 않습니다."
        >
          <Select options={YN_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="maintYn"
          label="점검(유지보수) 모드"
          extra="켜면 관리자를 제외한 모든 요청이 차단되고 아래 안내 문구가 표시됩니다. 관리자는 그대로 로그인·작업할 수 있습니다."
        >
          <Select options={YN_OPTIONS} />
        </Form.Item>
        <Form.Item name="maintMessage" label="점검 안내 문구">
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
        <Form.Item name="failCntLimit" label="로그인 실패 제한 횟수">
          <NumberInput />
        </Form.Item>
        <Form.Item name="failLockMins" label="실패 시 잠금 시간(분)">
          <NumberInput />
        </Form.Item>
        <Form.Item name="passwordExpireDays" label="비밀번호 만료 일수">
          <NumberInput />
        </Form.Item>
        <Form.Item name="sessionExpireMins" label="세션 만료(분)">
          <NumberInput />
        </Form.Item>
        <Form.Item name="delLogDays" label="로그 보관 수">
          <NumberInput />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          저장
        </Button>
      </Form>
    </Card>
  )
}
