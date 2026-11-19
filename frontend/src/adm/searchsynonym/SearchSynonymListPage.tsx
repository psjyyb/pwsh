import { Button, Card, Form, Input, Popconfirm, Space, Table, Tag } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { SEARCH_SYNONYM_LIST_URL, searchSynonymApi } from './searchsynonym.api'
import type { SearchSynonym } from './searchsynonym.api'

/**
 * 검색 동의어 관리(CMS 1.2.39) — 분할 마스터-디테일(평면).
 * 통합검색어 <b>전체</b>가 묶음의 한 말과 같으면 묶음의 말 전부로 찾는다(다른 말이 붙은 검색어엔 적용하지 않는다).
 * 한 말은 한 묶음에만 둘 수 있다 — 두 묶음에 있으면 어느 쪽으로 넓힐지 정할 수 없다(서버가 거절).
 */
export default function SearchSynonymListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<SearchSynonym>(SEARCH_SYNONYM_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<SearchSynonym>(searchSynonymApi, reload)
  const isEdit = mode === 'edit'

  const columns: TableColumnsType<SearchSynonym> = [
    {
      title: '동의어 묶음',
      dataIndex: 'words',
      render: (v?: string) => (
        <Space size={[4, 4]} wrap>
          {(v ?? '').split(',').filter(Boolean).map((w) => <Tag key={w} style={{ marginInlineEnd: 0 }}>{w}</Tag>)}
        </Space>
      ),
    },
    { title: '수정일', dataIndex: 'updDt', width: 140 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: '포함된 말', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<SearchSynonym>
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
            name="words"
            label="같은 뜻의 말"
            rules={[{ required: true, message: '같은 뜻의 말을 2개 이상 입력하세요.' }]}
            extra="쉼표나 줄바꿈으로 나눕니다(2~20개, 각 50자 이내). 대소문자·앞뒤 공백·연속 공백은 정리해 저장합니다. 예: 회원가입, 가입, sign up"
          >
            <Input.TextArea rows={6} maxLength={1000} />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="검색 동의어" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
