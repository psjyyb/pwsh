import { useState } from 'react'
import { Card, Descriptions, Table, Tag } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { MAIL_LOG_LIST_URL, mailLogApi } from './maillog.api'
import type { MailLog } from './maillog.api'

const STATUS_COLOR: Record<string, string> = { SUCCESS: 'green', FAIL: 'red', SKIP: 'default' }

/** 메일 발송 이력 — 분할(목록 | 상세 읽기전용, append-only). 성공·실패·미발송을 모두 남긴다. */
export default function MailLogListPage() {
  const { rows, total, loading, page, pageSize, search, changePage } = useList<MailLog>(MAIL_LOG_LIST_URL)
  const [selected, setSelected] = useState<MailLog | null>(null)

  // 목록에는 본문이 없다(무거움) — 행을 고르면 상세를 다시 읽어 본문까지 채운다.
  const openRow = async (r: MailLog) => {
    setSelected(r)
    setSelected(await mailLogApi.view(r.rowId!))
  }

  const columns: TableColumnsType<MailLog> = [
    {
      title: '결과',
      width: 90,
      render: (_, r) => <Tag color={STATUS_COLOR[r.statusCd ?? ''] ?? 'default'}>{r.statusName ?? r.statusCd}</Tag>,
    },
    { title: '템플릿', dataIndex: 'templateCd', width: 140 },
    { title: '수신자', dataIndex: 'toEmail', width: 200 },
    { title: '제목', dataIndex: 'subject' },
    { title: '일시', dataIndex: 'regDt', width: 170 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[
          { type: 'code', name: 'statusCd', pCodeId: 'MAIL00', placeholder: '발송결과' },
          { type: 'text', name: 'filterKeyword', placeholder: '제목/수신자', width: 240 },
        ]}
        onSearch={(v) => search(v)}
      />
      <Table<MailLog>
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
        <>
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="결과">
              <Tag color={STATUS_COLOR[selected.statusCd ?? ''] ?? 'default'}>
                {selected.statusName ?? selected.statusCd}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="템플릿">{selected.templateCd ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="수신자">{selected.toEmail}</Descriptions.Item>
            <Descriptions.Item label="제목">{selected.subject}</Descriptions.Item>
            <Descriptions.Item label="사유/경고">{selected.errorMsg ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="요청자">{selected.regId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="일시">{selected.regDt}</Descriptions.Item>
          </Descriptions>
          {selected.content && (
            <div style={{ marginTop: 12 }}>
              <div style={{ marginBottom: 6, fontWeight: 600 }}>발송 본문</div>
              {/* 저장된 HTML을 그대로 innerHTML에 넣으면 관리화면에서 스크립트가 실행된다 → 샌드박스 iframe */}
              <iframe
                title="발송 본문"
                sandbox=""
                srcDoc={selected.content}
                style={{ width: '100%', height: 360, border: '1px solid #f0f0f0', borderRadius: 6, background: '#fff' }}
              />
            </div>
          )}
        </>
      )}
    </Card>
  )

  return (
    <Card title="메일 발송 이력" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
