import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Space, Table, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { mailLogApi } from '../maillog/maillog.api'
import { MAIL_TEMPLATE_LIST_URL, mailTemplateApi } from './mailtemplate.api'
import type { MailPreview, MailTemplate } from './mailtemplate.api'

/** "키=값" 한 줄씩 → 객체. 치환 값 입력은 이 정도면 충분하다(JSON을 손으로 쓰게 하지 않는다). */
function parseVars(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  text.split('\n').forEach((line) => {
    const i = line.indexOf('=')
    if (i > 0) out[line.slice(0, i).trim()] = line.slice(i + 1).trim()
  })
  return out
}

/** 템플릿 본문의 {{키}}를 모아 "키=" 입력 초안을 만든다 */
function varDraft(tpl: MailTemplate): string {
  const found = new Set<string>()
  const re = /\{\{\s*([A-Za-z0-9_.]+)\s*\}\}/g
  for (const src of [tpl.subject ?? '', tpl.content ?? '']) {
    let m
    while ((m = re.exec(src)) !== null) found.add(m[1])
  }
  return [...found].map((k) => `${k}=`).join('\n')
}

/** 메일 템플릿 관리 — 분할 마스터-디테일(평면) + 미리보기/테스트 발송. */
export default function MailTemplateListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } =
    useList<MailTemplate>(MAIL_TEMPLATE_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<MailTemplate>(mailTemplateApi, reload)
  const isEdit = mode === 'edit'

  const [testOpen, setTestOpen] = useState(false)
  const [varsText, setVarsText] = useState('')
  const [toEmail, setToEmail] = useState('')
  const [preview, setPreview] = useState<MailPreview | null>(null)
  const [busy, setBusy] = useState(false)

  const current = (): MailTemplate => form.getFieldsValue()

  const openTest = () => {
    const tpl = current()
    if (!tpl.templateCd) {
      message.warning('템플릿을 먼저 선택하세요.')
      return
    }
    setVarsText(varDraft(tpl))
    setPreview(null)
    setTestOpen(true)
  }

  const doPreview = async () => {
    setBusy(true)
    try {
      setPreview(await mailTemplateApi.preview(current().templateCd!, parseVars(varsText)))
    } finally {
      setBusy(false)
    }
  }

  const doSend = async () => {
    if (!toEmail.trim()) {
      message.warning('수신자를 입력하세요.')
      return
    }
    setBusy(true)
    try {
      const ok = await mailLogApi.send(current().templateCd!, toEmail.trim(), parseVars(varsText))
      // 서버가 실제 발송 여부를 그대로 돌려준다 — 실패·미발송도 이력에 남으므로 거기서 사유를 본다.
      if (ok) message.success('발송했습니다. 결과는 메일발송이력에서 확인하세요.')
      else message.warning('발송되지 않았습니다. 메일발송이력에서 사유를 확인하세요.')
    } finally {
      setBusy(false)
    }
  }

  const columns: TableColumnsType<MailTemplate> = [
    { title: '코드', dataIndex: 'templateCd', width: 160 },
    { title: '이름', dataIndex: 'name', width: 160 },
    { title: '제목', dataIndex: 'subject' },
    { title: '수정일', dataIndex: 'updDt', width: 120, render: (v: string) => (v ? v.slice(0, 10) : '') },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: '코드/이름', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<MailTemplate>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r.rowId!), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const detail = (
    <Card
      title="상세 / 등록 / 수정"
      extra={
        <Space>
          <Button onClick={openNew}>신규</Button>
          <Button onClick={openTest} disabled={!isEdit}>미리보기·테스트</Button>
          <Button type="primary" onClick={save} disabled={mode === 'none'}>저장</Button>
          <Popconfirm title="삭제하시겠습니까?" onConfirm={remove} okText="삭제" cancelText="취소" disabled={!isEdit}>
            <Button danger disabled={!isEdit}>삭제</Button>
          </Popconfirm>
        </Space>
      }
    >
      {mode === 'none' ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>행을 선택하거나 [신규]를 누르세요.</div>
      ) : (
        <Form form={form} layout="vertical">
          <Form.Item
            name="templateCd"
            label="템플릿 코드"
            extra="발송하는 코드가 이 값으로 템플릿을 찾습니다. 사용중인 것끼리 중복될 수 없습니다."
            rules={[{ required: true, message: '템플릿 코드를 입력하세요.' }]}
          >
            <Input placeholder="예: WELCOME" />
          </Form.Item>
          <Form.Item name="name" label="템플릿 이름" rules={[{ required: true, message: '이름을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="subject"
            label="메일 제목"
            extra="{{키}}를 쓰면 발송 시 값으로 바뀝니다."
            rules={[{ required: true, message: '제목을 입력하세요.' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="content"
            label="HTML 본문"
            extra="메일 클라이언트 호환을 위해 table 레이아웃 + 인라인 스타일을 쓰세요(외부 CSS·flex는 깨집니다)."
            rules={[{ required: true, message: '본문을 입력하세요.' }]}
          >
            <Input.TextArea rows={14} style={{ fontFamily: 'Consolas, monospace', fontSize: 12.5 }} />
          </Form.Item>
          <Form.Item name="variables" label="치환 키 안내" extra="운영자용 메모입니다. 동작에는 영향이 없습니다.">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <>
      <Card title="메일 템플릿" styles={{ body: { padding: 12 } }}>
        <SplitLayout list={list} detail={detail} />
      </Card>

      <Modal
        title="미리보기 · 테스트 발송"
        open={testOpen}
        onCancel={() => setTestOpen(false)}
        width={820}
        footer={[
          <Button key="close" onClick={() => setTestOpen(false)}>닫기</Button>,
          <Button key="preview" onClick={doPreview} loading={busy}>미리보기</Button>,
          <Button key="send" type="primary" onClick={doSend} loading={busy}>테스트 발송</Button>,
        ]}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <div style={{ marginBottom: 6, fontWeight: 600 }}>치환 값 (한 줄에 `키=값`)</div>
            <Input.TextArea
              rows={4}
              value={varsText}
              onChange={(e) => setVarsText(e.target.value)}
              style={{ fontFamily: 'Consolas, monospace', fontSize: 12.5 }}
            />
          </div>
          <div>
            <div style={{ marginBottom: 6, fontWeight: 600 }}>수신자</div>
            <Input value={toEmail} onChange={(e) => setToEmail(e.target.value)} placeholder="test@example.com" />
          </div>
          {preview && (
            <>
              {preview.missing && (
                <Alert
                  type="warning"
                  showIcon
                  message={`값이 없어 지워진 키: ${preview.missing}`}
                  description="이대로 보내면 그 자리는 빈칸으로 나갑니다."
                />
              )}
              <div>
                <div style={{ marginBottom: 6, fontWeight: 600 }}>제목</div>
                <div style={{ padding: '8px 12px', background: '#fafafa', border: '1px solid #f0f0f0', borderRadius: 6 }}>
                  {preview.subject}
                </div>
              </div>
              <div>
                <div style={{ marginBottom: 6, fontWeight: 600 }}>본문</div>
                {/* 미리보기는 관리자가 방금 저장한 자기 템플릿이다. 렌더는 srcdoc(샌드박스 iframe)으로만 —
                    본문을 그대로 innerHTML에 넣으면 관리화면 자체에 스크립트가 실행된다. */}
                <iframe
                  title="메일 본문 미리보기"
                  sandbox=""
                  srcDoc={preview.content}
                  style={{ width: '100%', height: 380, border: '1px solid #f0f0f0', borderRadius: 6, background: '#fff' }}
                />
              </div>
            </>
          )}
        </Space>
      </Modal>
    </>
  )
}
