import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Card, Collapse, Empty, Form, Input, Popconfirm, Space, Spin, message } from 'antd'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { isAdmin } from '../../auth/token'
import { fileApi } from '../../api/file'
import RichTextEditor from '../../common/adm/components/RichTextEditor'
import type { RichTextEditorHandle } from '../../common/adm/components/RichTextEditor'
import { extractEditorImageIds } from '../../common/util/editorImages'
import type { Bbsinfo } from '../../adm/bbsinfo/bbsinfo.api'
import { BBS_LIST_URL, bbsApi } from '../../adm/bbs/bbs.api'
import type { Bbs } from '../../adm/bbs/bbs.api'
import SafeHtml from '../../common/SafeHtml'

/** FAQ 스킨 — 질문(제목) 클릭 시 답변(내용) 아코디언으로 펼침. 작성/수정/삭제는 관리자만. */
export default function FaqBoard({ board }: { board: Bbsinfo }) {
  const bbsinfoId = board.dbKey
  const admin = isAdmin()

  const [rows, setRows] = useState<Bbs[]>([])
  const [loading, setLoading] = useState(false)
  const [answers, setAnswers] = useState<Record<string, string>>({}) // bbsId -> 답변 HTML(펼칠 때 지연 로드)
  const [mode, setMode] = useState<'list' | 'write'>('list')
  const [editKey, setEditKey] = useState<string | null>(null)
  const [form] = Form.useForm()
  const editorRef = useRef<RichTextEditorHandle>(null)
  const [context, setContext] = useState('')

  const load = useCallback(async () => {
    if (!bbsinfoId) return
    setLoading(true)
    try {
      const res = await apiPost<ListResult<Bbs>>(BBS_LIST_URL, { bbsinfoId, pageIndex: 1, size: 200 })
      setRows(res.list)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '목록 조회 실패')
    } finally {
      setLoading(false)
    }
  }, [bbsinfoId])

  useEffect(() => {
    setMode('list')
    load()
  }, [load])

  // 아코디언 펼칠 때 답변(본문) 지연 로드(조회수 미증가)
  const onChange = async (keys: string | string[]) => {
    const arr = Array.isArray(keys) ? keys : [keys]
    const id = arr[arr.length - 1]
    if (id && answers[id] === undefined) {
      try {
        const p = await bbsApi.view(id)
        setAnswers((m) => ({ ...m, [id]: p.context ?? '' }))
      } catch {
        /* 무시 */
      }
    }
  }

  const openWrite = () => {
    setEditKey(null)
    setContext('')
    form.resetFields()
    setMode('write')
  }
  const openEdit = async (r: Bbs) => {
    try {
      const p = await bbsApi.view(r.dbKey!)
      setEditKey(r.dbKey!)
      setContext(p.context ?? '')
      form.setFieldsValue({ title: p.title })
      setMode('write')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '불러오기 실패')
    }
  }
  const save = async () => {
    const v = await form.validateFields()
    const html = editorRef.current?.getHTML() ?? context
    if (!html || !html.replace(/<[^>]*>/g, '').trim()) {
      message.warning('답변 내용을 입력하세요.')
      return
    }
    try {
      const payload = { bbsinfoId, title: v.title, context: html }
      const id = editKey ? (await bbsApi.update({ ...payload, dbKey: editKey }), editKey) : await bbsApi.insert(payload)
      // 본문(답변) 에디터 삽입 이미지 추적(고아 판별용)
      await fileApi.saveMapping(id, 'BBS_EDITOR', extractEditorImageIds(html))
      message.success('저장되었습니다.')
      setAnswers({})
      setMode('list')
      load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장 실패')
    }
  }
  const remove = async (id: string) => {
    try {
      await bbsApi.remove(id)
      message.success('삭제되었습니다.')
      setAnswers({})
      load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '삭제 실패')
    }
  }

  if (mode === 'write') {
    return (
      <Card
        title={editKey ? 'FAQ 수정' : 'FAQ 등록'}
        extra={
          <Space>
            <Button onClick={() => setMode('list')}>목록</Button>
            <Button type="primary" onClick={save}>저장</Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="질문" rules={[{ required: true, message: '질문을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="답변" required>
            <RichTextEditor key={`${editKey ?? 'new'}-editor`} ref={editorRef} initialHtml={context} uploadImage={fileApi.uploadImage} />
          </Form.Item>
        </Form>
      </Card>
    )
  }

  const items = rows.map((r) => ({
    key: r.dbKey!,
    label: (
      <span>
        <b style={{ color: '#1677ff', marginRight: 8 }}>Q</b>
        {r.title}
      </span>
    ),
    children: (
      <div>
        <SafeHtml className="toastui-editor-contents" html={answers[r.dbKey!] ?? '불러오는 중…'} />
        {admin && (
          <Space style={{ marginTop: 8 }}>
            <a onClick={() => openEdit(r)}>수정</a>
            <Popconfirm title="삭제하시겠습니까?" onConfirm={() => remove(r.dbKey!)} okText="삭제" cancelText="취소">
              <a>삭제</a>
            </Popconfirm>
          </Space>
        )}
      </div>
    ),
  }))

  return (
    <Card title={board.bbsinfoNm ?? 'FAQ'} extra={admin ? <Button type="primary" onClick={openWrite}>등록</Button> : null}>
      <Spin spinning={loading}>
        {rows.length === 0 ? <Empty description="등록된 FAQ가 없습니다." /> : <Collapse accordion items={items} onChange={onChange} />}
      </Spin>
    </Card>
  )
}
