import { useEffect, useRef, useState } from 'react'
import { Button, Card, Form, Input, Popconfirm, Select, Space, Table, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import CodeSelect from '../../common/adm/components/CodeSelect'
import NumberInput from '../../common/adm/components/NumberInput'
import ImageUpload from '../../common/adm/components/ImageUpload'
import RichTextEditor from '../../common/adm/components/RichTextEditor'
import type { RichTextEditorHandle } from '../../common/adm/components/RichTextEditor'
import { extractEditorImageIds } from '../../common/util/editorImages'
import { fileApi } from '../../api/file'
import { bbsinfoApi } from '../bbsinfo/bbsinfo.api'
import type { Bbsinfo } from '../bbsinfo/bbsinfo.api'
import { HOBBY_LIST_URL, hobbyApi } from './hobby.api'
import type { Hobby } from './hobby.api'

const THUMB_LOC = 'HOBBY'       // 대표이미지
const EDITOR_LOC = 'HOBBY_EDITOR' // 본문(소개/가이드) 삽입 이미지(고아 추적용)
type Mode = 'none' | 'insert' | 'edit'

/** 취미관리 — 카탈로그 CRUD. 소개/가이드=리치에디터, 대표이미지=업로드. */
export default function HobbyListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<Hobby>(HOBBY_LIST_URL)
  const [mode, setMode] = useState<Mode>('none')
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [form] = Form.useForm()
  const [boards, setBoards] = useState<Bbsinfo[]>([])
  const [seq, setSeq] = useState(0) // 에디터 remount 키
  const [introHtml, setIntroHtml] = useState('')
  const [guideHtml, setGuideHtml] = useState('')
  const introRef = useRef<RichTextEditorHandle>(null)
  const guideRef = useRef<RichTextEditorHandle>(null)

  useEffect(() => { bbsinfoApi.comboList().then(setBoards).catch(() => {}) }, [])

  const openNew = () => {
    form.resetFields()
    // 노출 순서 기본값 = 기존 취미 최대값 + 1
    const nextSort = rows.length ? Math.max(0, ...rows.map((r) => Number(r.sortNo) || 0)) + 1 : 1
    form.setFieldsValue({ sortNo: String(nextSort) })
    setIntroHtml(''); setGuideHtml('')
    setSelectedKey(null); setSeq((s) => s + 1); setMode('insert')
  }

  const openRow = async (rowId: string) => {
    try {
      const h = await hobbyApi.view(rowId)
      form.resetFields()
      form.setFieldsValue({
        hobbyNm: h.hobbyNm, summary: h.summary, difficultyCd: h.difficultyCd, bbsinfoId: h.bbsinfoId,
        sortNo: h.sortNo, equipment: h.equipment, estCost: h.estCost, thumbId: h.thumbId,
      })
      setIntroHtml(h.intro ?? ''); setGuideHtml(h.guide ?? '')
      setSelectedKey(rowId); setSeq((s) => s + 1); setMode('edit')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회 실패')
    }
  }

  const save = async () => {
    const v = await form.validateFields()
    const intro = introRef.current?.getHTML() ?? introHtml
    const guide = guideRef.current?.getHTML() ?? guideHtml
    const payload: Partial<Hobby> = {
      hobbyNm: v.hobbyNm, summary: v.summary ?? '', difficultyCd: v.difficultyCd ?? '', bbsinfoId: v.bbsinfoId ?? '',
      sortNo: v.sortNo ?? '0', equipment: v.equipment ?? '', estCost: v.estCost ?? '', intro, guide,
    }
    try {
      const id = mode === 'edit'
        ? (await hobbyApi.update({ ...payload, rowId: selectedKey! }), selectedKey!)
        : await hobbyApi.insertReturnId(payload)
      await fileApi.saveMapping(id, THUMB_LOC, v.thumbId ? [v.thumbId] : [])
      await fileApi.saveMapping(id, EDITOR_LOC, extractEditorImageIds(`${intro}${guide}`))
      message.success('저장되었습니다.')
      setMode('none'); setSelectedKey(null); form.resetFields()
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장 실패')
    }
  }

  const remove = async () => {
    if (!selectedKey) return
    try {
      await hobbyApi.remove(selectedKey)
      message.success('삭제되었습니다.')
      setMode('none'); setSelectedKey(null); form.resetFields()
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '삭제 실패')
    }
  }

  const columns: TableColumnsType<Hobby> = [
    { title: '취미명', dataIndex: 'hobbyNm' },
    { title: '난이도', width: 90, render: (_, r) => r.difficultyNm ?? '-' },
    { title: '연결 게시판', width: 130, render: (_, r) => r.bbsinfoNm ?? '-' },
    { title: '순서', dataIndex: 'sortNo', width: 70 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar fields={[{ type: 'text', name: 'filterKeyword', placeholder: '취미명', width: 220 }]} onSearch={(v) => search(v)} />
      <Table<Hobby>
        rowKey="rowId" size="small" columns={columns} dataSource={rows} loading={loading}
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
          <Button type="primary" onClick={save} disabled={mode === 'none'}>저장</Button>
          <Popconfirm title="삭제하시겠습니까?" onConfirm={remove} okText="삭제" cancelText="취소" disabled={mode !== 'edit'}>
            <Button danger disabled={mode !== 'edit'}>삭제</Button>
          </Popconfirm>
        </Space>
      }
    >
      {mode === 'none' ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>행을 선택하거나 [신규]를 누르세요.</div>
      ) : (
        <Form form={form} layout="vertical" initialValues={{ sortNo: '0' }}>
          <Form.Item name="hobbyNm" label="취미명" rules={[{ required: true, message: '취미명을 입력하세요.' }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="summary" label="한줄 소개">
            <Input maxLength={200} placeholder="예: 가까운 산부터 시작하는 건강한 취미" />
          </Form.Item>
          <Space size={8} wrap align="baseline">
            <Form.Item name="difficultyCd" label="난이도" style={{ minWidth: 160 }}>
              <CodeSelect pCodeId="HOBBYLV00" placeholder="난이도 선택" allowClear />
            </Form.Item>
            <Form.Item name="bbsinfoId" label="연결 게시판(소통)" style={{ minWidth: 240 }}
              extra={mode === 'insert' ? '비우면 취미 전용 게시판을 자동 생성' : undefined}>
              <Select allowClear placeholder="비우면 자동 생성" options={boards.map((b) => ({ value: b.rowId, label: b.bbsinfoNm }))} />
            </Form.Item>
            <Form.Item name="sortNo" label="노출 순서"><NumberInput /></Form.Item>
          </Space>
          <Form.Item name="thumbId" label="대표 이미지">
            <ImageUpload />
          </Form.Item>
          <Form.Item name="equipment" label="필요 장비">
            <Input maxLength={500} placeholder="예: 운동화(입문)/등산화, 배낭, 물통" />
          </Form.Item>
          <Form.Item name="estCost" label="대략 비용">
            <Input maxLength={200} placeholder="예: 입문 5만원 내외" />
          </Form.Item>
          <Form.Item label="소개">
            <RichTextEditor key={`intro-${seq}`} ref={introRef} initialHtml={introHtml} uploadImage={fileApi.uploadImage} />
          </Form.Item>
          <Form.Item label="입문 가이드">
            <RichTextEditor key={`guide-${seq}`} ref={guideRef} initialHtml={guideHtml} uploadImage={fileApi.uploadImage} />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="취미관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
