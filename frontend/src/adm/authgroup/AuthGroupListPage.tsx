import { useState } from 'react'
import { Button, Card, Form, Input, Popconfirm, Space, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { AUTH_GROUP_LIST_URL, authGroupApi } from './authgroup.api'
import type { AuthGroup } from './authgroup.api'
import AuthGroupMenuModal from './AuthGroupMenuModal'
import AuthGroupMemberModal from './AuthGroupMemberModal'

/** 권한그룹 관리 — 분할 마스터-디테일(평면) + 메뉴권한/사용자 지정 모달. */
export default function AuthGroupListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<AuthGroup>(AUTH_GROUP_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<AuthGroup>(authGroupApi, reload)
  const [menuTarget, setMenuTarget] = useState<AuthGroup | null>(null)
  const [memberTarget, setMemberTarget] = useState<AuthGroup | null>(null)
  const isEdit = mode === 'edit'

  const target = (): AuthGroup => ({ rowId: selectedKey!, authGroupName: form.getFieldValue('authGroupName') })

  const columns: TableColumnsType<AuthGroup> = [
    { title: '그룹ID', dataIndex: 'rowId', width: 180 },
    { title: '그룹명', dataIndex: 'authGroupName' },
    { title: '사용', dataIndex: 'useYn', width: 60 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: '그룹ID/명', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<AuthGroup>
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
          <Button onClick={() => setMenuTarget(target())} disabled={!isEdit}>권한설정</Button>
          <Button onClick={() => setMemberTarget(target())} disabled={!isEdit}>사용자</Button>
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
          <Form.Item name="rowId" label="그룹 ID" rules={[{ required: true, message: '그룹 ID를 입력하세요.' }]}>
            <Input disabled={isEdit} placeholder="예: EDITOR_GROUP" />
          </Form.Item>
          <Form.Item name="authGroupName" label="그룹명" rules={[{ required: true, message: '그룹명을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="설명">
            <Input />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="권한그룹 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
      <AuthGroupMenuModal open={menuTarget !== null} authgroup={menuTarget} onClose={() => setMenuTarget(null)} />
      <AuthGroupMemberModal open={memberTarget !== null} authgroup={memberTarget} onClose={() => setMemberTarget(null)} />
    </Card>
  )
}
