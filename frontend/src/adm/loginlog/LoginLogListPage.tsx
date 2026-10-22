import { useState } from 'react'
import { Card, Descriptions, Table, Tag } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { LOGINLOG_LIST_URL } from './loginlog.api'
import type { LoginLog } from './loginlog.api'

/** 성공(LOGIN01)만 초록, 나머지는 실패 — 실패가 눈에 띄어야 공격 시도를 빨리 본다 */
const resultTag = (r: LoginLog) => (
  <Tag color={r.resultCd === 'LOGIN01' ? 'green' : 'red'}>{r.resultName ?? r.resultCd}</Tag>
)

/** 로그인 이력 — 분할(목록 | 상세 읽기전용, append-only). 성공·실패 모두 자동기록. */
export default function LoginLogListPage() {
  const { rows, total, loading, page, pageSize, search, changePage } = useList<LoginLog>(LOGINLOG_LIST_URL)
  const [selected, setSelected] = useState<LoginLog | null>(null)

  const columns: TableColumnsType<LoginLog> = [
    { title: '일시', dataIndex: 'regDt', width: 170 },
    { title: '아이디', dataIndex: 'loginId', width: 150, ellipsis: true },
    { title: '결과', width: 170, render: (_, r) => resultTag(r) },
    { title: 'IP', dataIndex: 'regIp', width: 130 },
    { title: '기기', dataIndex: 'deviceType', width: 90 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[
          { type: 'daterange', name: 'regDt', fromName: 'filterFrom', toName: 'filterTo' },
          { type: 'code', name: 'resultCd', pCodeId: 'LOGIN00', placeholder: '결과' },
          {
            type: 'keyword',
            conditions: [
              { value: 'login_id', label: '아이디' },
              { value: 'reg_ip', label: 'IP' },
            ],
            width: 220,
          },
        ]}
        onSearch={(v) => search(v)}
      />
      <Table<LoginLog>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selected?.rowId ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => setSelected(r), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const detail = (
    <Card title="상세">
      {!selected ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>행을 선택하세요.</div>
      ) : (
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="일시">{selected.regDt}</Descriptions.Item>
          <Descriptions.Item label="아이디">{selected.loginId}</Descriptions.Item>
          <Descriptions.Item label="결과">{resultTag(selected)}</Descriptions.Item>
          <Descriptions.Item label="안내문">{selected.failMessage ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="IP">{selected.regIp ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="기기">{selected.deviceType}</Descriptions.Item>
          <Descriptions.Item label="User-Agent">{selected.userAgent ?? '-'}</Descriptions.Item>
        </Descriptions>
      )}
    </Card>
  )

  return (
    <Card title="로그인 이력" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
