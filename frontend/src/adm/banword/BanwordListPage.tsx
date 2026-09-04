import { Button, Card, Form, Input, Popconfirm, Space, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { BANWORD_LIST_URL, banwordApi } from './banword.api'
import type { Banword } from './banword.api'

/**
 * 금칙어 관리 — 분할 마스터-디테일(평면).
 * 등록된 단어가 게시글 제목·본문과 댓글에 포함되면 서버가 등록을 거부한다(대소문자·공백 무시).
 */
export default function BanwordListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<Banword>(BANWORD_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<Banword>(banwordApi, reload)
  const isEdit = mode === 'edit'

  const columns: TableColumnsType<Banword> = [
    { title: '금칙어', dataIndex: 'word' },
    { title: '사용', dataIndex: 'useYn', width: 60 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: '금칙어', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<Banword>
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
            name="word"
            label="금칙어"
            rules={[{ required: true, message: '금칙어를 입력하세요.' }]}
            extra="대소문자·공백을 무시하고 부분일치로 검사합니다. (예: '무 료 광 고'도 '무료광고'로 걸립니다)"
          >
            <Input maxLength={100} />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="금칙어관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
