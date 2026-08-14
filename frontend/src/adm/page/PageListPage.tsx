import { useEffect, useRef, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Segmented, Space, Table, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import RichTextEditor from '../../common/adm/components/RichTextEditor'
import type { RichTextEditorHandle } from '../../common/adm/components/RichTextEditor'
import { runWithMessage } from '../../common/util/action'
import { PAGE_LIST_URL, pageApi } from './page.api'
import type { Page } from './page.api'
import SafeHtml from '../../common/SafeHtml'

type ContentMode = 'html' | 'editor'

/**
 * 일반페이지 관리 — 전용 페이지 방식(목록↔편집 전환, 전체폭).
 * 본문은 raw HTML 저장 → 사용자 화면(GenPageView)이 그대로 렌더.
 *  - HTML 직접입력: 코드 그대로 입력(렌더됨). / 에디터: WYSIWYG(비개발자용, sanitizer 적용).
 *  - 미리보기: 저장 화면과 동일하게 HTML 렌더 확인.
 */
export default function PageListPage() {
  const { rows, total, loading, page, size, reload, search, changePage } = useList<Page>(PAGE_LIST_URL)
  const [mode, setMode] = useState<'list' | 'form'>('list')
  const [editKey, setEditKey] = useState<string | null>(null)
  const [pendingTitle, setPendingTitle] = useState('')
  const [context, setContext] = useState('') // 본문 HTML(단일 소스)
  const [contentMode, setContentMode] = useState<ContentMode>('html')
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewHtml, setPreviewHtml] = useState('')
  const [form] = Form.useForm()
  const editorRef = useRef<RichTextEditorHandle>(null)
  const editorDirty = useRef(false) // 에디터에서 실제 편집했는지

  useEffect(() => {
    if (mode === 'form') form.setFieldsValue({ title: pendingTitle })
  }, [mode, pendingTitle, form])

  /** 현재 본문 HTML. 에디터에서 "실제 편집"했을 때만 에디터값, 그 외엔 원본 HTML(context) 그대로 */
  const currentHtml = () =>
    contentMode === 'editor' && editorDirty.current ? editorRef.current?.getHTML() ?? context : context

  const openNew = () => {
    setEditKey(null)
    setPendingTitle('')
    setContext('')
    setContentMode('html')
    editorDirty.current = false
    setMode('form')
  }
  const openEdit = async (row: Page) => {
    try {
      const data = await pageApi.view(row.rowId!)
      setEditKey(row.rowId!)
      setPendingTitle(data.title ?? '')
      setContext(data.context ?? '')
      setContentMode('html')
      editorDirty.current = false
      setMode('form')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회에 실패했습니다.')
    }
  }

  /**
   * 편집 모드 전환.
   * HTML→에디터: 현재 HTML을 에디터에 로드.
   * 에디터→HTML: 에디터에서 실제 편집했을 때만 반영(그대로 두면 raw HTML 원본 보존 — 단순 확인 시 변형 방지).
   */
  const switchMode = (m: ContentMode) => {
    if (m === contentMode) return
    if (contentMode === 'editor') {
      const edited = editorRef.current?.getHTML() ?? ''
      if (editorDirty.current) setContext(edited) // 에디터에서 수정한 경우만 반영
      editorDirty.current = false
    }
    setContentMode(m)
  }

  const openPreview = () => {
    setPreviewHtml(currentHtml()) // 현재 편집 화면 기준(raw 보존, context는 건드리지 않음)
    setPreviewOpen(true)
  }

  const save = async () => {
    const values = await form.validateFields()
    const html = currentHtml()
    try {
      if (editKey) await pageApi.update({ rowId: editKey, title: values.title, context: html })
      else await pageApi.insert({ title: values.title, context: html })
      message.success('저장되었습니다.')
      setMode('list')
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }
  const remove = (row: Page) => runWithMessage(() => pageApi.remove(row.rowId!), '삭제되었습니다.', reload)

  const columns: TableColumnsType<Page> = [
    { title: '페이지ID', dataIndex: 'rowId', width: 110 },
    { title: '제목', dataIndex: 'title' },
    { title: '사용', dataIndex: 'useYn', width: 70 },
    {
      title: '관리',
      width: 180,
      render: (_, row) => (
        <Space>
          <Button onClick={() => openEdit(row)}>수정</Button>
          <Popconfirm title="삭제하시겠습니까?" onConfirm={() => remove(row)} okText="삭제" cancelText="취소">
            <Button danger>삭제</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  if (mode === 'form') {
    return (
      <Card
        title={editKey ? '페이지 수정' : '페이지 등록'}
        extra={
          <Space>
            <Button onClick={openPreview}>미리보기</Button>
            <Button onClick={() => setMode('list')}>목록</Button>
            <Button type="primary" onClick={save}>저장</Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="제목" rules={[{ required: true, message: '제목을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="본문" required>
            <Space style={{ marginBottom: 8 }}>
              <Segmented
                value={contentMode}
                onChange={(v) => switchMode(v as ContentMode)}
                options={[
                  { label: 'HTML 직접입력', value: 'html' },
                  { label: '에디터', value: 'editor' },
                ]}
              />
              <span style={{ color: '#999', fontSize: 12 }}>
                HTML 코드를 그대로 입력하면 사용자 화면에 그대로 렌더됩니다.
              </span>
            </Space>
            {contentMode === 'html' ? (
              <Input.TextArea
                value={context}
                onChange={(e) => setContext(e.target.value)}
                autoSize={{ minRows: 16, maxRows: 40 }}
                placeholder="<div>...</div>"
                style={{ fontFamily: 'monospace', fontSize: 13 }}
              />
            ) : (
              <RichTextEditor
                key={`${editKey ?? 'new'}-editor`}
                ref={editorRef}
                initialHtml={context}
                onChange={() => { editorDirty.current = true }}
              />
            )}
          </Form.Item>
        </Form>

        <Modal open={previewOpen} onCancel={() => setPreviewOpen(false)} footer={null} title="미리보기" width={860}>
          <SafeHtml html={previewHtml} />
        </Modal>
      </Card>
    )
  }

  return (
    <Card title="일반페이지 관리">
      <SearchBar
        fields={[{ type: 'text', name: 'searchKeyword', placeholder: '제목 검색', width: 260 }]}
        onSearch={(v) => search(v)}
        onCreate={openNew}
        createText="신규"
      />
      <Table<Page>
        rowKey="rowId"
        scroll={{ x: 'max-content' }}
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{ current: page, pageSize: size, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )
}
