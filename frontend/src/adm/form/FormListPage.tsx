import { useState } from 'react'
import { Button, Card, Checkbox, Form, Input, Popconfirm, Space, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import CodeSelect from '../../common/adm/components/CodeSelect'
import DateField from '../../common/adm/components/DateField'
import YnSelect from '../../common/adm/components/YnSelect'
import { CHOICE_FIELDS, FORM_LIST_URL, formApi } from './form.api'
import type { Form as FormVO, FormField } from './form.api'

const EMPTY_FIELD: FormField = { label: '', fieldCd: 'FIELD01', requiredYn: 'N', privacyYn: 'N' }

/**
 * 폼 설정(신청·민원·설문 공용) — 분할 마스터-디테일.
 *
 * <p>문항은 별도 화면으로 빼지 않고 여기서 같이 편집한다(저장 한 번 = 요청 1건).
 * 사용자 화면 노출은 메뉴관리에서 연결유형 '폼'으로 이 폼을 고르면 된다.
 */
export default function FormListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<FormVO>(FORM_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, remove } = useSplitForm<FormVO>(formApi, reload)
  const isEdit = mode === 'edit'

  // 문항은 폼 객체 안에 있어서 AntD Form.Item으로 다루기 번거롭다 → 별도 상태로 들고 저장 때 합친다
  const [fields, setFields] = useState<FormField[]>([])

  // openRow가 이미 view.do로 전체(문항 포함)를 읽어 폼에 넣는다 → 같은 요청을 두 번 보내지 않는다
  const loadRow = async (rowId: string) => {
    await openRow(rowId)
    setFields(form.getFieldValue('fields') ?? [])
  }

  const startNew = () => {
    openNew()
    setFields([{ ...EMPTY_FIELD }])
  }

  const setField = (idx: number, patch: Partial<FormField>) =>
    setFields((prev) => prev.map((f, i) => (i === idx ? { ...f, ...patch } : f)))

  const moveField = (idx: number, dir: -1 | 1) =>
    setFields((prev) => {
      const next = [...prev]
      const to = idx + dir
      if (to < 0 || to >= next.length) return prev
      ;[next[idx], next[to]] = [next[to], next[idx]]
      return next
    })

  const save = async () => {
    const values = await form.validateFields()
    if (fields.length === 0) {
      message.warning('문항을 하나 이상 추가하세요.')
      return
    }
    const bad = fields.find((f) => !f.label?.trim())
    if (bad) {
      message.warning('문항 제목을 입력하세요.')
      return
    }
    try {
      const payload: FormVO = { ...values, fields }
      if (isEdit) await formApi.update({ ...payload, rowId: selectedKey! })
      else await formApi.insert(payload)
      message.success('저장되었습니다.')
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  const columns: TableColumnsType<FormVO> = [
    { title: '유형', dataIndex: 'typeName', width: 70 },
    { title: '제목', dataIndex: 'title' },
    {
      title: '접수기간',
      width: 190,
      render: (_, r) => `${r.startDt || '제한없음'} ~ ${r.endDt || '제한없음'}`,
    },
    { title: '응답', dataIndex: 'answerCnt', width: 70, align: 'right' },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[
          { type: 'code', name: 'typeCd', pCodeId: 'FORM00', placeholder: '폼 유형' },
          { type: 'text', name: 'filterKeyword', placeholder: '제목', width: 220 },
        ]}
        onSearch={(v) => search(v)}
      />
      <Table<FormVO>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => loadRow(r.rowId!), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const fieldEditor = (
    <div style={{ marginTop: 8 }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
        <b>문항</b>
        <Button size="small" style={{ marginLeft: 'auto' }} onClick={() => setFields((p) => [...p, { ...EMPTY_FIELD }])}>
          문항 추가
        </Button>
      </div>
      {fields.map((f, i) => (
        <Card key={f.rowId ?? `new-${i}`} size="small" style={{ marginBottom: 8 }} styles={{ body: { padding: 10 } }}>
          <Space direction="vertical" style={{ width: '100%' }} size={6}>
            <Space size={6} wrap>
              <Input
                placeholder="문항 제목"
                value={f.label}
                style={{ width: 240 }}
                onChange={(e) => setField(i, { label: e.target.value })}
              />
              <CodeSelect
                pCodeId="FIELD00"
                value={f.fieldCd}
                style={{ width: 130 }}
                onChange={(v) => setField(i, { fieldCd: v as string })}
              />
              <Checkbox checked={f.requiredYn === 'Y'} onChange={(e) => setField(i, { requiredYn: e.target.checked ? 'Y' : 'N' })}>
                필수
              </Checkbox>
              <Checkbox checked={f.privacyYn === 'Y'} onChange={(e) => setField(i, { privacyYn: e.target.checked ? 'Y' : 'N' })}>
                개인정보
              </Checkbox>
              <Button size="small" onClick={() => moveField(i, -1)}>▲</Button>
              <Button size="small" onClick={() => moveField(i, 1)}>▼</Button>
              <Button size="small" danger onClick={() => setFields((p) => p.filter((_, x) => x !== i))}>
                삭제
              </Button>
            </Space>
            {CHOICE_FIELDS.includes(f.fieldCd ?? '') ? (
              <Input.TextArea
                rows={3}
                placeholder="선택지를 한 줄에 하나씩"
                value={f.options}
                onChange={(e) => setField(i, { options: e.target.value })}
              />
            ) : (
              <Input
                placeholder="입력 안내문(선택)"
                value={f.placeholder}
                onChange={(e) => setField(i, { placeholder: e.target.value })}
              />
            )}
            {f.privacyYn === 'Y' && (
              <Tag color="orange">답이 암호화 저장되고, 열람이 개인정보 접근로그에 남습니다</Tag>
            )}
          </Space>
        </Card>
      ))}
    </div>
  )

  const detail = (
    <Card
      title="상세 / 등록 / 수정"
      extra={
        <Space>
          <Button onClick={startNew}>신규</Button>
          <Button type="primary" onClick={save} disabled={mode === 'none'}>저장</Button>
          <Popconfirm
            title="폼을 삭제하면 문항과 응답도 함께 내려갑니다. 삭제할까요?"
            onConfirm={remove}
            okText="삭제"
            cancelText="취소"
            disabled={!isEdit}
          >
            <Button danger disabled={!isEdit}>삭제</Button>
          </Popconfirm>
        </Space>
      }
    >
      {mode === 'none' ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>행을 선택하거나 [신규]를 누르세요.</div>
      ) : (
        <>
          <Form form={form} layout="vertical" initialValues={{ typeCd: 'FORM01', loginYn: 'Y', multiYn: 'N' }}>
            <Form.Item name="title" label="폼 제목" rules={[{ required: true, message: '제목을 입력하세요.' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="typeCd" label="폼 유형" rules={[{ required: true, message: '유형을 선택하세요.' }]}>
              <CodeSelect pCodeId="FORM00" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="description" label="안내문" extra="응답 화면 상단에 표시됩니다.">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Space size={8} wrap>
              <Form.Item name="startDt" label="접수 시작일">
                <DateField allowClear placeholder="시작일" />
              </Form.Item>
              <Form.Item name="endDt" label="접수 종료일">
                <DateField allowClear placeholder="종료일" />
              </Form.Item>
            </Space>
            <Space size={8} wrap>
              <Form.Item name="loginYn" label="로그인 필요" extra="N이면 비로그인도 제출">
                <YnSelect />
              </Form.Item>
              <Form.Item name="multiYn" label="중복 제출 허용" extra="N이면 1인 1회(로그인 제출 기준)">
                <YnSelect />
              </Form.Item>
            </Space>
            <Form.Item name="doneMessage" label="제출 완료 문구">
              <Input.TextArea rows={2} placeholder="비우면 기본 문구가 나옵니다." />
            </Form.Item>
          </Form>
          {fieldEditor}
        </>
      )}
    </Card>
  )

  return (
    <Card title="폼 설정" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
