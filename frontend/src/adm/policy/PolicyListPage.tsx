import { Button, Card, Form, Input, Popconfirm, Space, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import CodeSelect from '../../common/adm/components/CodeSelect'
import { runWithMessage } from '../../common/util/action'
import { POLICY_LIST_URL, policyApi } from './policy.api'
import type { Policy } from './policy.api'

/** 약관/정책 관리 — 분할 마스터-디테일(평면). */
export default function PolicyListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<Policy>(POLICY_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<Policy>(policyApi, reload)
  const isEdit = mode === 'edit'

  const move = (row: Policy, dir: 'UP' | 'DOWN') =>
    runWithMessage(() => policyApi.moveOrdr(row.rowId!, dir), '순서를 변경했습니다.', reload)

  const columns: TableColumnsType<Policy> = [
    { title: '제목', dataIndex: 'title' },
    { title: '유형', width: 130, render: (_, r) => r.typeCdNm ?? r.typeCd },
    { title: '필수', dataIndex: 'reqYn', width: 60 },
    {
      title: '순서',
      width: 100,
      render: (_, row) => (
        <Space size={4} onClick={(e) => e.stopPropagation()}>
          <span>{row.ordr}</span>
          <Button size="small" onClick={() => move(row, 'UP')}>▲</Button>
          <Button size="small" onClick={() => move(row, 'DOWN')}>▼</Button>
        </Space>
      ),
    },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[
          { type: 'text', name: 'filterKeyword', placeholder: '제목' },
          { type: 'code', name: 'typeCd', pCodeId: 'POLICY00' },
        ]}
        onSearch={(v) => search(v)}
      />
      <Table<Policy>
        rowKey="rowId"
        scroll={{ x: 'max-content' }}
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
        <Form form={form} layout="vertical" initialValues={{ reqYn: 'Y' }}>
          <Form.Item name="title" label="제목" rules={[{ required: true, message: '제목을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="typeCd" label="약관유형" rules={[{ required: true, message: '약관유형을 선택하세요.' }]}>
            <CodeSelect pCodeId="POLICY00" placeholder="약관유형 선택" />
          </Form.Item>
          <Form.Item name="content" label="내용">
            <Input.TextArea rows={8} />
          </Form.Item>
          <Form.Item name="reqYn" label="필수동의 여부(Y/N)">
            <Input />
          </Form.Item>
          {isEdit && (
            <Form.Item name="ordr" label="정렬순서(순서변경은 목록의 ▲▼)">
              <Input disabled />
            </Form.Item>
          )}
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="약관/정책 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
