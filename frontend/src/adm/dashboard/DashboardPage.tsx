import { useEffect, useState } from 'react'
import { Card, Col, Row, Spin, Statistic, Table, Tag, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { configApi } from '../config/config.api'
import { USER_LIST_URL } from '../user/user.api'
import { AUTHGRP_LIST_URL } from '../authgrp/authgrp.api'
import { MENU_LIST_URL } from '../menu/menu.api'
import { CODE_LIST_URL } from '../code/code.api'
import { BBSINFO_LIST_URL } from '../bbsinfo/bbsinfo.api'
import { PAGE_LIST_URL } from '../page/page.api'
import { POPUP_LIST_URL } from '../popup/popup.api'
import { POLICY_LIST_URL } from '../policy/policy.api'
import { EVENTLOG_LIST_URL } from '../eventlog/eventlog.api'
import type { Eventlog } from '../eventlog/eventlog.api'

/** 대시보드 통계 카드: 각 도메인 목록 API의 totCnt로 건수 표시 (URL은 도메인 api의 단일 소스 상수 재사용) */
const CARDS = [
  { title: '사용자', url: USER_LIST_URL, color: '#1677ff' },
  { title: '권한그룹', url: AUTHGRP_LIST_URL, color: '#722ed1' },
  { title: '메뉴', url: MENU_LIST_URL, color: '#13c2c2' },
  { title: '공통코드', url: CODE_LIST_URL, color: '#52c41a' },
  { title: '게시판', url: BBSINFO_LIST_URL, color: '#fa8c16' },
  { title: '페이지', url: PAGE_LIST_URL, color: '#eb2f96' },
  { title: '팝업', url: POPUP_LIST_URL, color: '#faad14' },
  { title: '약관', url: POLICY_LIST_URL, color: '#2f54eb' },
]

const evTagColor = (t?: string) =>
  t === 'LOGIN' ? 'blue' : t === 'INSERT' ? 'green' : t === 'UPDATE' ? 'gold' : t === 'DELETE' ? 'red' : 'default'

/** 관리자 메인 대시보드 — 도메인별 건수 통계 + 최근 활동(이벤트 로그). */
export default function DashboardPage() {
  const [counts, setCounts] = useState<Record<string, number | null>>({}) // undefined=로딩, null=실패
  const [logs, setLogs] = useState<Eventlog[]>([])
  const [logLoading, setLogLoading] = useState(true)
  const [siteTitle, setSiteTitle] = useState('취만사')

  useEffect(() => {
    CARDS.forEach((c) => {
      apiPost<ListResult<unknown>>(c.url, { pageIndex: 1, size: 1 })
        .then((r) => setCounts((prev) => ({ ...prev, [c.title]: r.totCnt })))
        .catch(() => setCounts((prev) => ({ ...prev, [c.title]: null })))
    })
    apiPost<ListResult<Eventlog>>(EVENTLOG_LIST_URL, { pageIndex: 1, size: 8 })
      .then((r) => setLogs(r.list))
      .catch(() => {})
      .finally(() => setLogLoading(false))
    configApi.view().then((c) => { if (c.title) setSiteTitle(c.title) }).catch(() => {})
  }, [])

  const logColumns: TableColumnsType<Eventlog> = [
    { title: '일시', dataIndex: 'regDt', width: 150 },
    {
      title: '행위',
      width: 90,
      render: (_, r) => <Tag color={evTagColor(r.eventType)}>{r.eventTypeNm ?? r.eventType}</Tag>,
    },
    { title: '수행자', dataIndex: 'userId', width: 120 },
    { title: '대상', render: (_, r) => (r.targetTable ? `${r.targetTable}${r.targetId ? ' #' + r.targetId : ''}` : '-') },
  ]

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>{siteTitle} 관리</Typography.Title>
      <Row gutter={[16, 16]}>
        {CARDS.map((c) => {
          const v = counts[c.title]
          return (
            <Col xs={12} sm={8} lg={6} key={c.title}>
              <Card styles={{ body: { padding: 20 } }} style={{ borderTop: `3px solid ${c.color}` }}>
                <Statistic
                  title={c.title}
                  value={v ?? 0}
                  valueStyle={{ color: c.color, fontWeight: 600 }}
                  formatter={() => (v === undefined ? <Spin size="small" /> : v === null ? '-' : v.toLocaleString())}
                />
              </Card>
            </Col>
          )
        })}
      </Row>
      <Card title="최근 활동" style={{ marginTop: 16 }} styles={{ body: { padding: 0 } }}>
        <Table<Eventlog>
          rowKey="dbKey"
          size="small"
          loading={logLoading}
          columns={logColumns}
          dataSource={logs}
          pagination={false}
          locale={{ emptyText: '최근 활동이 없습니다.' }}
        />
      </Card>
    </div>
  )
}
