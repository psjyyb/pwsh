import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Select, Space, Tag, message } from 'antd'
import NumberInput from '../../common/adm/components/NumberInput'
import { configItemApi } from './configitem.api'
import type { ConfigItem } from './configitem.api'

/** group_cd → 카드 제목. 없는 그룹은 코드값을 그대로 보여준다(정의만 추가하고 여기 안 넣어도 화면은 나온다). */
const GROUP_LABEL: Record<string, string> = {
  SITE: '사이트',
  MAIN: '메인 화면',
}

const YN_OPTIONS = [
  { value: 'Y', label: '사용' },
  { value: 'N', label: '사용안함' },
]

/**
 * 확장 설정 — 항목이 늘어도 이 화면은 그대로다.
 *
 * 서버가 준 정의(name·inputType·groupCd·sortNo)만 보고 입력칸을 만든다.
 * 그래서 설정 항목 추가가 data.sql 한 줄로 끝난다 — 이 파일을 고칠 일이 없다.
 */
export default function ConfigItemListPage() {
  const [form] = Form.useForm()
  const [items, setItems] = useState<ConfigItem[]>([])
  const [loading, setLoading] = useState(false)

  const load = () => {
    configItemApi
      .list()
      .then((rows) => {
        setItems(rows)
        form.setFieldsValue(Object.fromEntries(rows.map((r) => [r.configKey, r.value ?? ''])))
      })
      .catch((e) => message.error(e instanceof Error ? e.message : '조회에 실패했습니다.'))
  }

  useEffect(load, [])

  const onSave = async () => {
    setLoading(true)
    try {
      const values = form.getFieldsValue()
      await configItemApi.updateValues(
        items.map((it) => ({ configKey: it.configKey, value: String(values[it.configKey] ?? '') })),
      )
      message.success('저장되었습니다.')
      load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const renderInput = (item: ConfigItem) => {
    if (item.inputType === 'TEXTAREA') return <Input.TextArea rows={3} maxLength={500} showCount />
    if (item.inputType === 'NUMBER') return <NumberInput />
    if (item.inputType === 'YN') return <Select options={YN_OPTIONS} />
    return <Input maxLength={500} />
  }

  // 서버가 group_cd → sort_no 순으로 정렬해 주므로 순서를 유지하며 그룹만 묶는다
  const groups: { code: string; rows: ConfigItem[] }[] = []
  for (const item of items) {
    const code = item.groupCd || 'ETC'
    const last = groups[groups.length - 1]
    if (last && last.code === code) last.rows.push(item)
    else groups.push({ code, rows: [item] })
  }

  return (
    <Card
      title="확장설정"
      extra={
        <Button type="primary" onClick={onSave} loading={loading} disabled={items.length === 0}>
          저장
        </Button>
      }
      styles={{ body: { padding: 12 } }}
    >
      {items.length === 0 ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>등록된 확장 설정이 없습니다.</div>
      ) : (
        <Form form={form} layout="vertical" style={{ maxWidth: 640 }}>
          {groups.map((g) => (
            <Card key={g.code} type="inner" title={GROUP_LABEL[g.code] ?? g.code} style={{ marginBottom: 12 }}>
              {g.rows.map((item) => (
                <Form.Item
                  key={item.configKey}
                  // ★ 배열로 감싼다 — 설정 키에 점(.)이 있어 문자열로 주면 AntD가 중첩 경로
                  //   ('site.footer-text' → {site:{'footer-text':…}})로 해석해 값이 어긋난다.
                  name={[item.configKey]}
                  label={
                    <Space size={4}>
                      <span>{item.name}</span>
                      {item.publicYn === 'Y' && <Tag color="blue">공개</Tag>}
                    </Space>
                  }
                  extra={
                    <>
                      {item.description}
                      <div style={{ color: '#bbb', fontSize: 12 }}>{item.configKey}</div>
                    </>
                  }
                >
                  {renderInput(item)}
                </Form.Item>
              ))}
            </Card>
          ))}
        </Form>
      )}
    </Card>
  )
}
