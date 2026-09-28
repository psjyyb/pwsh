import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Col, Descriptions, Popconfirm, Progress, Row, Space, Spin, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { systemApi } from './system.api'
import type { Migration, SystemCache, SystemSchedule, SystemStatus } from './system.api'

/** MB → 사람이 읽는 크기 */
function mb(v?: number) {
  if (v === undefined || v === null) return '-'
  return v >= 1024 ? `${(v / 1024).toFixed(1)} GB` : `${v.toLocaleString()} MB`
}

/**
 * 시스템 상태 — 지금 떠 있는 것이 어떤 상태인지 한 장으로 본다.
 *
 * <p>여기 있는 값은 전부 <b>읽기 전용</b>이다. 앱이 pg_dump를 돌리거나 파일시스템을 건드리게
 * 만들지 않았다 — 실행 권한·경로·용량이 배포 환경마다 다르고, 실패해도 조용히 반쪽 백업이 남는다.
 * 실제 DB 백업은 운영 절차(cron + pg_dump)로 하고, 이 화면은 그 절차에 필요한 정보와
 * 설정 스냅샷만 준다.
 */
export default function SystemStatusPage() {
  const [data, setData] = useState<SystemStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await systemApi.status())
    } catch (e) {
      message.error(e instanceof Error ? e.message : '상태 조회에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const evictCache = async () => {
    setBusy(true)
    try {
      const n = await systemApi.evictCache()
      message.success(`캐시 ${n}개를 비웠습니다.`)
      load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '캐시 비우기에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const download = async () => {
    setBusy(true)
    try {
      await systemApi.downloadBackup()
      message.success('설정 스냅샷을 내려받았습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '내려받기에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const migrationColumns: TableColumnsType<Migration> = [
    { title: '버전', dataIndex: 'version', width: 80 },
    { title: '내용', dataIndex: 'description' },
    {
      title: '결과',
      width: 80,
      render: (_, r) => (r.success ? <Tag color="green">성공</Tag> : <Tag color="red">실패</Tag>),
    },
    { title: '소요(ms)', dataIndex: 'executionTime', width: 90 },
    { title: '적용일시', dataIndex: 'installedOn', width: 170 },
  ]

  const cacheColumns: TableColumnsType<SystemCache> = [
    { title: '캐시', dataIndex: 'name' },
    { title: '항목', dataIndex: 'size', width: 80 },
    { title: '적중', dataIndex: 'hit', width: 80 },
    { title: '실패', dataIndex: 'miss', width: 80 },
    { title: '적중률', width: 90, render: (_, r) => (r.hitRate === undefined ? '-' : `${r.hitRate}%`) },
  ]

  const scheduleColumns: TableColumnsType<SystemSchedule> = [
    { title: '배치', dataIndex: 'name' },
    { title: 'cron', dataIndex: 'cron', width: 160 },
  ]

  if (loading && !data) {
    return <Card><div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div></Card>
  }

  const app = data?.app ?? {}
  const db = data?.db ?? {}
  const disk = data?.disk ?? {}
  const heapPct = app.heapMaxMb ? Math.round(((app.heapUsedMb ?? 0) / app.heapMaxMb) * 100) : 0
  const diskUsedPct =
    disk.totalMb && disk.freeMb !== undefined
      ? Math.round(((disk.totalMb - disk.freeMb) / disk.totalMb) * 100)
      : 0

  return (
    <Card
      title="시스템 상태"
      extra={
        <Space>
          <Button onClick={load} loading={loading}>새로고침</Button>
          <Popconfirm
            title="캐시 비우기"
            description="메모리 캐시를 모두 비웁니다. DB에서 설정을 직접 바꿨을 때 씁니다."
            onConfirm={evictCache}
            okText="비우기"
            cancelText="취소"
          >
            <Button loading={busy}>캐시 비우기</Button>
          </Popconfirm>
          <Button type="primary" onClick={download} loading={busy}>설정 스냅샷 받기</Button>
        </Space>
      }
    >
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="애플리케이션" size="small">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="버전">
                {app.version} {app.cmsVersion && app.cmsVersion !== app.version && `· CMS ${app.cmsVersion}`}
              </Descriptions.Item>
              <Descriptions.Item label="빌드시각">{app.buildTime ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="프로파일">{app.profiles || '-'}</Descriptions.Item>
              <Descriptions.Item label="Java">{app.javaVersion}</Descriptions.Item>
              {/* 배포 시각이 아니라 마지막 재시작 이후 경과다 */}
              <Descriptions.Item label="가동시간">{app.uptime}</Descriptions.Item>
              <Descriptions.Item label="힙 메모리">
                <Space direction="vertical" size={2} style={{ width: '100%' }}>
                  <span>{mb(app.heapUsedMb)} / {mb(app.heapMaxMb)}</span>
                  <Progress percent={heapPct} size="small" status={heapPct > 85 ? 'exception' : 'normal'} />
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="메일 발송">
                {app.mailEnabled === 'Y' ? <Tag color="green">켜짐</Tag> : <Tag>꺼짐(이력만 기록)</Tag>}
              </Descriptions.Item>
              {/* 쪽지·알림 푸시(SSE). 이 서버에 붙은 연결만 센다 — 인스턴스를 늘리면 합계가 아니다 */}
              <Descriptions.Item label="실시간 연결">
                {(data?.realtime?.connections ?? 0).toLocaleString()}개
                {' · '}
                {(data?.realtime?.members ?? 0).toLocaleString()}명
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="데이터베이스 · 디스크" size="small">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="DB">{db.version ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="DB 크기">{db.size ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="업로드 경로">{disk.path ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="업로드 사용량">
                {mb(disk.uploadMb)} · {(disk.uploadFiles ?? 0).toLocaleString()}개
              </Descriptions.Item>
              <Descriptions.Item label="디스크">
                <Space direction="vertical" size={2} style={{ width: '100%' }}>
                  <span>여유 {mb(disk.freeMb)} / 전체 {mb(disk.totalMb)}</span>
                  <Progress percent={diskUsedPct} size="small" status={diskUsedPct > 90 ? 'exception' : 'normal'} />
                </Space>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card
            title="스키마 마이그레이션"
            size="small"
            styles={{ body: { padding: 0 } }}
            extra={<span style={{ fontSize: 12, color: '#999' }}>최근 10건</span>}
          >
            <Table<Migration>
              rowKey={(r) => `${r.version}-${r.installedOn}`}
              size="small"
              columns={migrationColumns}
              dataSource={db.migrations ?? []}
              pagination={false}
              locale={{ emptyText: 'Flyway 이력이 없습니다(수동 구축 DB).' }}
            />
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card title="캐시" size="small" styles={{ body: { padding: 0 } }}>
              <Table<SystemCache>
                rowKey="name"
                size="small"
                columns={cacheColumns}
                dataSource={data?.caches ?? []}
                pagination={false}
              />
            </Card>
            <Card title="배치" size="small" styles={{ body: { padding: 0 } }}>
              <Table<SystemSchedule>
                rowKey="name"
                size="small"
                columns={scheduleColumns}
                dataSource={data?.schedules ?? []}
                pagination={false}
              />
            </Card>
          </Space>
        </Col>
      </Row>
    </Card>
  )
}
