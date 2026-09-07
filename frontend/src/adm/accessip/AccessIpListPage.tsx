import { Alert, Button, Card, Form, Input, Popconfirm, Space, Table, Tag } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { ACCESSIP_LIST_URL, accessIpApi } from './accessip.api'
import type { AccessIp } from './accessip.api'

/**
 * 접속 IP 관리 — 분할 마스터-디테일(평면).
 * 환경설정의 [접속 IP 제한]이 켜져 있을 때, 여기 등록된 IP에서만 관리자 기능을 쓸 수 있다.
 * 목록이 비어 있으면 제한이 동작하지 않는다(전원 잠금 방지).
 */
export default function AccessIpListPage() {
  const { rows, total, loading, page, pageSize, extra, reload, search, changePage } =
    useList<AccessIp>(ACCESSIP_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<AccessIp>(accessIpApi, reload)
  const isEdit = mode === 'edit'

  const myIp = (extra.myIp as string) ?? ''
  const enforced = extra.enforcedYn === 'Y'

  const columns: TableColumnsType<AccessIp> = [
    {
      title: 'IP',
      dataIndex: 'ip',
      render: (v: string) => (
        <Space size={4}>
          <span>{v}</span>
          {v === myIp && <Tag color="blue">현재 접속</Tag>}
        </Space>
      ),
    },
    { title: '설명', dataIndex: 'description' },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: 'IP · 설명', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<AccessIp>
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
            name="ip"
            label="IP"
            rules={[{ required: true, message: 'IP를 입력하세요.' }]}
            extra={
              <>
                정확히 일치(192.168.0.10) 또는 대역(192.168.0.0/24)으로 입력합니다.
                {myIp && (
                  <>
                    {' '}
                    <a onClick={() => form.setFieldValue('ip', myIp)}>내 IP({myIp}) 넣기</a>
                  </>
                )}
              </>
            }
          >
            <Input maxLength={45} placeholder="192.168.0.10 또는 192.168.0.0/24" />
          </Form.Item>
          <Form.Item name="description" label="설명">
            <Input maxLength={255} placeholder="사무실 · VPN 등 어디인지" />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="접속IP관리" styles={{ body: { padding: 12 } }}>
      <Alert
        style={{ marginBottom: 12 }}
        type={enforced ? 'warning' : 'info'}
        showIcon
        message={enforced ? '접속 IP 제한이 동작 중입니다.' : '접속 IP 제한이 꺼져 있습니다.'}
        description={
          enforced
            ? `등록된 IP에서만 관리자 기능을 사용할 수 있습니다. 현재 접속 IP: ${myIp || '확인 불가'}`
            : `환경설정의 [접속 IP 제한]을 켜면 아래 목록의 IP에서만 관리자 기능을 쓸 수 있습니다. 목록이 비어 있으면 제한은 동작하지 않습니다. 현재 접속 IP: ${myIp || '확인 불가'}`
        }
      />
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
