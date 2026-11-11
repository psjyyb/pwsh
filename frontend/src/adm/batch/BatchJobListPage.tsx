import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Input, Modal, Popconfirm, Space, Switch, Table, Tag, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import { BATCH_LOG_LIST_URL, batchApi } from './batch.api'
import type { BatchJob, BatchLog } from './batch.api'

/** 실행 중은 파랑, 성공 초록, 실패 빨강 — 실패가 눈에 띄어야 한다 */
const statusTag = (cd?: string, name?: string) => {
  if (!cd) return <Tag>실행 기록 없음</Tag>
  const color = cd === 'BATCH01' ? 'processing' : cd === 'BATCH02' ? 'green' : 'red'
  return <Tag color={color}>{name ?? cd}</Tag>
}

/** 자주 쓰는 주기. 직접 입력도 된다(6자리: 초 분 시 일 월 요일) */
const CRON_PRESETS = [
  { label: '매일 새벽 3시', value: '0 0 3 * * *' },
  { label: '매일 새벽 4시', value: '0 0 4 * * *' },
  { label: '매시 정각', value: '0 0 * * * *' },
  { label: '매주 일요일 새벽 4시', value: '0 0 4 * * SUN' },
  { label: '매월 1일 새벽 4시', value: '0 0 4 1 * *' },
]

/** 즉시 실행 확인 문구 — 회원 라이프사이클은 휴면 전환·파기가 실제로 일어난다 */
const runWarning = (job: BatchJob) =>
  job.jobCd === 'MEMBER_LIFECYCLE'
    ? '휴면 예정 안내메일 발송·휴면 전환·개인정보 파기가 실제로 실행됩니다. 지금 실행할까요?'
    : `${job.name}을(를) 지금 실행합니다. 작업은 뒤에서 돌고 결과는 아래 실행 이력에 남습니다.`

/**
 * 배치 작업 관리 — 주기 변경·자동 실행 켜고 끄기·즉시 실행·실행 이력.
 * 주기는 DB(batch_job)가 원본이고 저장하면 재시작 없이 다음 회차부터 반영된다.
 */
export default function BatchJobListPage() {
  const [jobs, setJobs] = useState<BatchJob[]>([])
  const [loadingJobs, setLoadingJobs] = useState(false)
  const [editing, setEditing] = useState<BatchJob | null>(null)
  const [cronText, setCronText] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const logs = useList<BatchLog>(BATCH_LOG_LIST_URL)

  const loadJobs = useCallback(async () => {
    setLoadingJobs(true)
    try {
      setJobs((await batchApi.list()).list)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회에 실패했습니다.')
    } finally {
      setLoadingJobs(false)
    }
  }, [])

  useEffect(() => {
    loadJobs()
  }, [loadJobs])

  // 실행 중인 작업이 있으면 끝날 때까지 3초마다 다시 읽는다(즉시 실행은 바로 응답하고 뒤에서 돈다)
  const anyRunning = jobs.some((j) => j.lastStatusCd === 'BATCH01')
  useEffect(() => {
    if (!anyRunning) return
    const t = window.setInterval(() => {
      loadJobs()
      logs.reload()
    }, 3000)
    return () => window.clearInterval(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anyRunning, loadJobs])

  const refreshAll = () => {
    loadJobs()
    logs.reload()
  }

  const save = async (job: BatchJob, cronExpr: string, autoYn: string) => {
    setBusyId(job.rowId!)
    try {
      await batchApi.update(job.rowId!, cronExpr, autoYn)
      message.success('저장했습니다. 다음 회차부터 반영됩니다.')
      setEditing(null)
      loadJobs()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  const run = async (job: BatchJob) => {
    setBusyId(job.rowId!)
    try {
      await batchApi.run(job.rowId!)
      message.success('실행을 시작했습니다. 결과는 실행 이력에서 확인하세요.')
      refreshAll()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '실행에 실패했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  const jobColumns: TableColumnsType<BatchJob> = [
    {
      title: '작업',
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.name}</div>
          <div style={{ color: '#888', fontSize: 12 }}>{r.description}</div>
        </div>
      ),
    },
    {
      title: '주기',
      width: 210,
      render: (_, r) => (
        <div>
          <Typography.Text code>{r.cronExpr}</Typography.Text>
          <div style={{ color: '#888', fontSize: 12, marginTop: 2 }}>
            {r.nextRunDt ? `다음 실행 ${r.nextRunDt}` : '자동 실행 안 함'}
          </div>
        </div>
      ),
    },
    {
      title: '자동 실행',
      width: 90,
      render: (_, r) => (
        <Switch
          checked={r.autoYn === 'Y'}
          loading={busyId === r.rowId}
          onChange={(on) => save(r, r.cronExpr!, on ? 'Y' : 'N')}
        />
      ),
    },
    {
      title: '마지막 실행',
      width: 280,
      render: (_, r) => (
        <div>
          <Space size={4}>
            {statusTag(r.lastStatusCd, r.lastStatusName)}
            {r.lastManualYn === 'Y' && <Tag>즉시 실행</Tag>}
            <span style={{ color: '#888', fontSize: 12 }}>{r.lastStartDt}</span>
          </Space>
          {r.lastResultMsg && (
            <div style={{ color: r.lastStatusCd === 'BATCH03' ? '#cf1322' : '#555', fontSize: 12, marginTop: 2 }}>
              {r.lastResultMsg}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '',
      width: 190,
      render: (_, r) => (
        <Space>
          <Button
            size="small"
            onClick={() => {
              setEditing(r)
              setCronText(r.cronExpr ?? '')
            }}
          >
            주기 변경
          </Button>
          <Popconfirm title="즉시 실행" description={runWarning(r)} onConfirm={() => run(r)} okText="실행" cancelText="취소">
            <Button size="small" type="primary" loading={busyId === r.rowId} disabled={r.lastStatusCd === 'BATCH01'}>
              즉시 실행
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const logColumns: TableColumnsType<BatchLog> = [
    { title: '시작', dataIndex: 'startDt', width: 160 },
    { title: '종료', dataIndex: 'endDt', width: 160, render: (v?: string) => v ?? '-' },
    { title: '작업', dataIndex: 'jobName', width: 150, render: (v, r) => v ?? r.jobCd },
    { title: '결과', width: 90, render: (_, r) => statusTag(r.statusCd, r.statusName) },
    { title: '방식', width: 120, render: (_, r) => (r.manualYn === 'Y' ? `즉시(${r.regId ?? '-'})` : '예약') },
    { title: '처리 내용', dataIndex: 'resultMsg', ellipsis: true },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="배치 작업 관리" extra={<Button onClick={refreshAll} loading={loadingJobs}>새로고침</Button>}>
        <Table<BatchJob>
          rowKey="rowId"
          size="small"
          columns={jobColumns}
          dataSource={jobs}
          loading={loadingJobs}
          pagination={false}
        />
      </Card>

      <Card title="실행 이력">
        <SearchBar
          fields={[
            {
              type: 'select',
              name: 'jobCd',
              placeholder: '작업',
              width: 180,
              options: jobs.map((j) => ({ value: j.jobCd!, label: j.name! })),
            },
            { type: 'code', name: 'statusCd', pCodeId: 'BATCH00', placeholder: '결과' },
          ]}
          onSearch={(v) => logs.search(v)}
        />
        <Table<BatchLog>
          rowKey="rowId"
          size="small"
          columns={logColumns}
          dataSource={logs.rows}
          loading={logs.loading}
          pagination={{
            current: logs.page,
            pageSize: logs.pageSize,
            total: logs.total,
            showSizeChanger: true,
            onChange: (p, ps) => logs.changePage(p, ps),
          }}
        />
      </Card>

      <Modal
        title={`주기 변경 — ${editing?.name ?? ''}`}
        open={!!editing}
        onCancel={() => setEditing(null)}
        onOk={() => editing && save(editing, cronText.trim(), editing.autoYn ?? 'Y')}
        okText="저장"
        cancelText="취소"
        confirmLoading={!!editing && busyId === editing.rowId}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={10}>
          <Alert
            type="info"
            showIcon
            message="초 분 시 일 월 요일 6자리로 입력합니다"
            description="예) 0 0 4 * * * = 매일 새벽 4시, 0 30 3 * * * = 매일 새벽 3시 30분. 1분보다 잦게는 저장되지 않습니다. 저장하면 재시작 없이 다음 회차부터 반영됩니다."
          />
          <Input value={cronText} onChange={(e) => setCronText(e.target.value)} style={{ fontFamily: 'Consolas, monospace' }} />
          <Space wrap size={6}>
            {CRON_PRESETS.map((p) => (
              <Button key={p.value} size="small" onClick={() => setCronText(p.value)}>
                {p.label}
              </Button>
            ))}
          </Space>
        </Space>
      </Modal>
    </Space>
  )
}
