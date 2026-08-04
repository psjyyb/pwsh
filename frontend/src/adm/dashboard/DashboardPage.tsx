import { useEffect, useState } from 'react'
import { Card, Col, Row, Space, Spin, Statistic, Table, Tag, Typography } from 'antd'
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
import TrendChart from './TrendChart'
import type { TrendPoint } from './TrendChart'

/** 통계 응답(집계 전용 stats 도메인) */
interface StatsDaily { label: string; signups: number; posts: number; recruits: number }
interface StatsTotals {
  userCnt?: number; suspendedCnt?: number; postCnt?: number; commentCnt?: number
  recruitCnt?: number; recruitOpenCnt?: number; hobbyCnt?: number; reportPendingCnt?: number
}
interface StatsResp { totals?: StatsTotals; daily?: StatsDaily[]; days?: number }

/** 추이 3종 색 — dataviz 검증기 통과(라이트 표면, CVD ΔE 10.0 / 정상시야 24.6). 대비 WARN은 수치 라벨로 보완. */
const TREND_COLORS = { signups: '#6C4EE3', posts: '#12B586', recruits: '#F59321' }

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
  const [stats, setStats] = useState<StatsResp | null>(null)

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
    apiPost<StatsResp>('/adm/stats/selectStatsList.do', {}).then(setStats).catch(() => {})
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
      {/* 최근 추이 — 지표별 작은 다중(각 1시리즈). 이중축을 쓰지 않고 카드를 분리 */}
      {stats?.daily && stats.daily.length > 0 && (
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          {([
            ['가입', 'signups', TREND_COLORS.signups, '명'],
            ['게시글', 'posts', TREND_COLORS.posts, '건'],
            ['모집', 'recruits', TREND_COLORS.recruits, '건'],
          ] as [string, keyof StatsDaily, string, string][]).map(([label, key, color, unit]) => {
            const points: TrendPoint[] = stats.daily!.map((d) => ({ label: d.label, value: Number(d[key]) || 0 }))
            return (
              <Col xs={24} lg={8} key={key}>
                <Card size="small" title={`${label} 추이 (최근 ${stats.days ?? points.length}일)`}>
                  <TrendChart points={points} color={color} unit={unit} />
                </Card>
              </Col>
            )
          })}
        </Row>
      )}

      {/* 운영 현황 요약 — 조치가 필요한 값(정지 계정·미처리 신고)을 눈에 띄게 */}
      {stats?.totals && (
        <Card size="small" title="운영 현황" style={{ marginTop: 16 }}>
          <Space size={16} wrap>
            <span>회원 <b>{stats.totals.userCnt ?? 0}</b></span>
            <span>정지 <b style={{ color: (stats.totals.suspendedCnt ?? 0) > 0 ? '#cf1322' : undefined }}>{stats.totals.suspendedCnt ?? 0}</b></span>
            <span>미처리 신고 <b style={{ color: (stats.totals.reportPendingCnt ?? 0) > 0 ? '#cf1322' : undefined }}>{stats.totals.reportPendingCnt ?? 0}</b></span>
            <span>게시글 <b>{stats.totals.postCnt ?? 0}</b></span>
            <span>댓글 <b>{stats.totals.commentCnt ?? 0}</b></span>
            <span>모집 <b>{stats.totals.recruitCnt ?? 0}</b> (모집중 {stats.totals.recruitOpenCnt ?? 0})</span>
            <span>취미 <b>{stats.totals.hobbyCnt ?? 0}</b></span>
          </Space>
        </Card>
      )}

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
