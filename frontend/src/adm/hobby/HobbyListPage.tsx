import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Popconfirm, Select, Space, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import CodeSelect from '../../common/adm/components/CodeSelect'
import NumberInput from '../../common/adm/components/NumberInput'
import { bbsinfoApi } from '../bbsinfo/bbsinfo.api'
import type { Bbsinfo } from '../bbsinfo/bbsinfo.api'
import { HOBBY_LIST_URL, hobbyApi } from './hobby.api'
import type { Hobby } from './hobby.api'

/** 취미관리 — 취미 카탈로그(입문 소개/가이드 + 게시판 연결) 마스터-디테일 CRUD. */
export default function HobbyListPage() {
  const { rows, total, loading, page, size, reload, search, changePage } = useList<Hobby>(HOBBY_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<Hobby>(hobbyApi, reload)
  const isEdit = mode === 'edit'
  const [boards, setBoards] = useState<Bbsinfo[]>([])

  useEffect(() => {
    bbsinfoApi.comboList().then(setBoards).catch(() => {})
  }, [])

  const columns: TableColumnsType<Hobby> = [
    { title: '취미명', dataIndex: 'hobbyNm' },
    { title: '난이도', width: 90, render: (_, r) => r.difficultyNm ?? '-' },
    { title: '연결 게시판', width: 130, render: (_, r) => r.bbsinfoNm ?? '-' },
    { title: '순서', dataIndex: 'sortOrdr', width: 70 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'searchKeyword', placeholder: '취미명', width: 220 }]}
        onSearch={(v) => search(v)}
      />
      <Table<Hobby>
        rowKey="dbKey"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.dbKey === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r.dbKey!), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: size, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
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
          <Popconfirm title="삭제하시겠습니까?" onConfirm={remove} okText="삭제" cancelText="취소" disabled={!isEdit}>
            <Button danger disabled={!isEdit}>삭제</Button>
          </Popconfirm>
        </Space>
      }
    >
      {mode === 'none' ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>행을 선택하거나 [신규]를 누르세요.</div>
      ) : (
        <Form form={form} layout="vertical" initialValues={{ sortOrdr: '0' }}>
          <Form.Item name="hobbyNm" label="취미명" rules={[{ required: true, message: '취미명을 입력하세요.' }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="summary" label="한줄 소개">
            <Input maxLength={200} placeholder="예: 가까운 산부터 시작하는 건강한 취미" />
          </Form.Item>
          <Space size={8} wrap align="baseline">
            <Form.Item name="difficultyCd" label="난이도" style={{ minWidth: 160 }}>
              <CodeSelect pCodeId="HOBBYLV00" placeholder="난이도 선택" />
            </Form.Item>
            <Form.Item name="bbsinfoId" label="연결 게시판(소통)" style={{ minWidth: 200 }}>
              <Select
                allowClear placeholder="게시판 선택"
                options={boards.map((b) => ({ value: b.dbKey, label: b.bbsinfoNm }))}
              />
            </Form.Item>
            <Form.Item name="sortOrdr" label="노출 순서"><NumberInput /></Form.Item>
          </Space>
          <Form.Item name="equipment" label="필요 장비">
            <Input maxLength={500} placeholder="예: 운동화(입문)/등산화, 배낭, 물통" />
          </Form.Item>
          <Form.Item name="estCost" label="대략 비용">
            <Input maxLength={200} placeholder="예: 입문 5만원 내외" />
          </Form.Item>
          <Form.Item name="intro" label="소개 (HTML 가능)">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} placeholder="취미 소개 — 입문자가 무엇인지 이해할 수 있게" />
          </Form.Item>
          <Form.Item name="guide" label="입문 가이드 (HTML 가능)">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} placeholder="어떻게 시작하는지 단계별 안내" />
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
