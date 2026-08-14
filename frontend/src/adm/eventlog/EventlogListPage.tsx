import { useState } from 'react'
import { Card, Descriptions, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { EVENTLOG_LIST_URL } from './eventlog.api'
import type { Eventlog } from './eventlog.api'

/** 활동 로그 — 분할(목록 | 상세 읽기전용, append-only). 로그인/등록/수정/삭제 자동기록. */
export default function EventlogListPage() {
  const { rows, total, loading, page, pageSize, search, changePage } = useList<Eventlog>(EVENTLOG_LIST_URL)
  const [selected, setSelected] = useState<Eventlog | null>(null)

  const columns: TableColumnsType<Eventlog> = [
    { title: '유형', width: 90, render: (_, r) => r.eventTypeNm ?? r.eventType },
    { title: '수행자', dataIndex: 'userId', width: 130 },
    {
      title: '대상',
      render: (_, r) => (r.targetTable ? `${r.targetTable}${r.targetId ? ` #${r.targetId}` : ''}` : '-'),
    },
    { title: '기기', dataIndex: 'deviceType', width: 90 },
    { title: '일시', dataIndex: 'regDt', width: 170 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[
          { type: 'code', name: 'eventType', pCodeId: 'EVENT00' },
          {
            type: 'keyword',
            conditions: [
              { value: 'user_id', label: '수행자ID' },
              { value: 'target_table', label: '대상테이블' },
            ],
            width: 220,
          },
        ]}
        onSearch={(v) => search(v)}
      />
      <Table<Eventlog>
        rowKey="rowId"
        scroll={{ x: 'max-content' }}
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
          <Descriptions.Item label="이벤트ID">{selected.rowId}</Descriptions.Item>
          <Descriptions.Item label="유형">{selected.eventTypeNm ?? selected.eventType}</Descriptions.Item>
          <Descriptions.Item label="수행자">{selected.userId}</Descriptions.Item>
          <Descriptions.Item label="대상 테이블">{selected.targetTable ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="대상 ID">{selected.targetId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="기기">{selected.deviceType}</Descriptions.Item>
          <Descriptions.Item label="User-Agent">{selected.userAgent}</Descriptions.Item>
          <Descriptions.Item label="일시">{selected.regDt}</Descriptions.Item>
          <Descriptions.Item label="IP">{selected.regIp}</Descriptions.Item>
        </Descriptions>
      )}
    </Card>
  )

  return (
    <Card title="활동 로그" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
