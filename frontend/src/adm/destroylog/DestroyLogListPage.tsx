import { Card, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import { DESTROYLOG_LIST_URL } from './destroylog.api'
import type { DestroyLog } from './destroylog.api'

/**
 * 개인정보 파기 이력 — 조회 전용(append-only). 파기는 되돌릴 수 없어 "언제·누가·어떤 경로로"를 증빙한다.
 * 항목이 몇 개 안 돼 상세 패널 없이 목록 한 장으로 둔다.
 */
export default function DestroyLogListPage() {
  const { rows, total, loading, page, pageSize, search, changePage } = useList<DestroyLog>(DESTROYLOG_LIST_URL)

  const columns: TableColumnsType<DestroyLog> = [
    { title: '파기일시', dataIndex: 'regDt', width: 170 },
    { title: '회원 ID', dataIndex: 'memberId', width: 150 },
    { title: '유형', render: (_, r) => r.destroyName ?? r.destroyCd },
    { title: '탈퇴일시', width: 150, render: (_, r) => r.withdrawDt ?? '-' },
    { title: '처리자', width: 120, render: (_, r) => r.actorId ?? '자동' },
  ]

  return (
    <Card title="파기 이력">
      <SearchBar
        fields={[
          { type: 'daterange', name: 'regDt', fromName: 'filterFrom', toName: 'filterTo' },
          { type: 'code', name: 'destroyCd', pCodeId: 'DESTROY00', placeholder: '유형' },
          {
            type: 'keyword',
            conditions: [
              { value: 'member_id', label: '회원 ID' },
              { value: 'actor_id', label: '처리자' },
            ],
            width: 220,
          },
        ]}
        onSearch={(v) => search(v)}
      />
      <Table<DestroyLog>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{ current: page, pageSize: pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )
}
