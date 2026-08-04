import { useState } from 'react'
import { Button, Card, Form, Input, Popconfirm, Space, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { AUTHGRP_LIST_URL, authgrpApi } from './authgrp.api'
import type { Authgrp } from './authgrp.api'
import AuthgrpMenuModal from './AuthgrpMenuModal'
import AuthgrpUserModal from './AuthgrpUserModal'

/** 권한그룹 관리 — 분할 마스터-디테일(평면) + 메뉴권한/사용자 지정 모달. */
export default function AuthgrpListPage() {
  const { rows, total, loading, page, size, reload, search, changePage } = useList<Authgrp>(AUTHGRP_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<Authgrp>(authgrpApi, reload)
  const [menuTarget, setMenuTarget] = useState<Authgrp | null>(null)
  const [userTarget, setUserTarget] = useState<Authgrp | null>(null)
  const isEdit = mode === 'edit'

  const target = (): Authgrp => ({ dbKey: selectedKey!, authgrpNm: form.getFieldValue('authgrpNm') })

  const columns: TableColumnsType<Authgrp> = [
    { title: '그룹ID', dataIndex: 'dbKey', width: 180 },
    { title: '그룹명', dataIndex: 'authgrpNm' },
    { title: '사용', dataIndex: 'useYn', width: 60 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'searchKeyword', placeholder: '그룹ID/명', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<Authgrp>
        rowKey="dbKey"
        scroll={{ x: 'max-content' }}
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
          <Button onClick={() => setMenuTarget(target())} disabled={!isEdit}>권한설정</Button>
          <Button onClick={() => setUserTarget(target())} disabled={!isEdit}>사용자</Button>
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
          <Form.Item name="dbKey" label="그룹 ID" rules={[{ required: true, message: '그룹 ID를 입력하세요.' }]}>
            <Input disabled={isEdit} placeholder="예: EDITOR_GROUP" />
          </Form.Item>
          <Form.Item name="authgrpNm" label="그룹명" rules={[{ required: true, message: '그룹명을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="authgrpDesc" label="설명">
            <Input />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="권한그룹 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
      <AuthgrpMenuModal open={menuTarget !== null} authgrp={menuTarget} onClose={() => setMenuTarget(null)} />
      <AuthgrpUserModal open={userTarget !== null} authgrp={userTarget} onClose={() => setUserTarget(null)} />
    </Card>
  )
}
