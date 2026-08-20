import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, message } from 'antd'
import NumberInput from '../../common/adm/components/NumberInput'
import ImageUpload from '../../common/adm/components/ImageUpload'
import { fileApi } from '../../api/file'
import { configApi } from './config.api'
import type { Config } from './config.api'

const LOGO_MAP_KEY = '1' // config 단일행(config_id=1)에 로고 매핑

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
        <Form.Item name="accIpYn" label="접속 IP 제한(Y/N)">
          <Input />
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
