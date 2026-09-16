import { useState } from 'react'
import { Alert, Card, Descriptions, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { PRIVACYLOG_LIST_URL, privacylogApi } from './privacylog.api'
import type { Privacylog } from './privacylog.api'

/** 개인정보 접근 로그 — 분할(목록 | 상세 읽기전용, append-only). 복호화 조회가 자동 기록된다. */
export default function PrivacylogListPage() {
  const { rows, total, loading, page, pageSize, search, changePage } = useList<Privacylog>(PRIVACYLOG_LIST_URL)
  const [selected, setSelected] = useState<Privacylog | null>(null)

  // 목록에는 User-Agent가 없다(길다) — 행을 고르면 상세를 다시 읽는다.
  const openRow = async (r: Privacylog) => {
    setSelected(r)
    setSelected(await privacylogApi.view(r.rowId!))
  }

  const columns: TableColumnsType<Privacylog> = [
    { title: '조회자', dataIndex: 'memberId', width: 120 },
    { title: '대상', dataIndex: 'targetIds', ellipsis: true },
    { title: '건수', dataIndex: 'accessCnt', width: 70, align: 'right' },
    { title: '수행 업무', dataIndex: 'requestUri', width: 260, ellipsis: true },
    { title: '접속지', dataIndex: 'regIp', width: 120 },
    { title: '일시', dataIndex: 'regDt', width: 170 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: '조회자/대상/경로', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<Privacylog>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selected?.rowId ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r), style: { cursor: 'pointer' } })}
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
          <Descriptions.Item label="접근 ID">{selected.rowId}</Descriptions.Item>
          <Descriptions.Item label="조회자">{selected.memberId}</Descriptions.Item>
          <Descriptions.Item label="대상(정보주체)">{selected.targetIds ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="조회 건수">{selected.accessCnt}</Descriptions.Item>
          <Descriptions.Item label="수행 업무">{selected.requestUri}</Descriptions.Item>
          <Descriptions.Item label="조회 경로">{selected.sqlIds ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="기기">{selected.deviceType}</Descriptions.Item>
          <Descriptions.Item label="User-Agent">{selected.userAgent ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="접속지 IP">{selected.regIp}</Descriptions.Item>
          <Descriptions.Item label="일시">{selected.regDt}</Descriptions.Item>
        </Descriptions>
      )}
    </Card>
  )

  return (
    <Card title="개인정보 접근로그" styles={{ body: { padding: 12 } }}>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="개인정보를 복호화해 읽은 요청이 자동으로 기록됩니다(요청 1건 = 1행)."
        description="본인 정보만 읽은 요청과 비로그인 요청은 남기지 않습니다. 이 기록은 수정·삭제할 수 없으며, 보존기간은 운영 정책에 따라 직접 관리해야 합니다."
      />
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
