import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { reportApi } from '../../api/report'
import type { Report } from '../../api/report'

const TYPE_NM: Record<string, string> = { BBS: '게시글', COMMENT: '댓글', RECRUIT: '모집' }
const statusTag = (s?: string) =>
  s === 'RESOLVED' ? <Tag color="green">처리완료</Tag>
    : s === 'DISMISSED' ? <Tag>반려</Tag>
      : <Tag color="orange">대기</Tag>

/** 신고 관리(관리자) — 게시글/댓글/모집 신고 목록 + 처리(완료/반려). */
export default function ReportListPage() {
  const [rows, setRows] = useState<Report[]>([])
  const [status, setStatus] = useState<string | undefined>('PENDING')
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await reportApi.list(status))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '목록 조회 실패')
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => { load() }, [load])

  const handle = async (dbKey: string, next: string) => {
    try {
      await reportApi.updateStatus(dbKey, next)
      message.success(next === 'RESOLVED' ? '처리완료로 변경했습니다.' : '반려했습니다.')
      load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '처리 실패')
    }
  }

  const columns: TableColumnsType<Report> = [
    { title: '유형', width: 80, align: 'center', render: (_, r) => TYPE_NM[r.targetType ?? ''] ?? r.targetType },
    { title: '대상', render: (_, r) => r.targetTitle || `#${r.targetId}` },
    { title: '사유', render: (_, r) => r.reason, ellipsis: true },
    { title: '신고자', width: 120, render: (_, r) => r.regNm || '-' },
    { title: '신고일', dataIndex: 'regDt', width: 140 },
    { title: '상태', width: 90, align: 'center', render: (_, r) => statusTag(r.status) },
    {
      title: '처리', width: 170, align: 'center',
      render: (_, r) =>
        r.status === 'PENDING' ? (
          <Space>
            <Popconfirm title="처리완료로 변경?" onConfirm={() => handle(r.dbKey!, 'RESOLVED')} okText="확인" cancelText="취소">
              <Button size="small" type="primary">처리완료</Button>
            </Popconfirm>
            <Popconfirm title="반려하시겠습니까?" onConfirm={() => handle(r.dbKey!, 'DISMISSED')} okText="반려" cancelText="취소">
              <Button size="small">반려</Button>
            </Popconfirm>
          </Space>
        ) : (
          <a onClick={() => handle(r.dbKey!, 'PENDING')} style={{ fontSize: 12 }}>대기로</a>
        ),
    },
  ]

  return (
    <Card title="신고 관리">
      <Space style={{ marginBottom: 12 }}>
        <Select
          value={status} style={{ width: 140 }} onChange={setStatus}
          options={[
            { value: '', label: '전체' },
            { value: 'PENDING', label: '대기' },
            { value: 'RESOLVED', label: '처리완료' },
            { value: 'DISMISSED', label: '반려' },
          ]}
        />
        <Button onClick={load}>새로고침</Button>
      </Space>
      <Table<Report> rowKey="dbKey" size="small" scroll={{ x: 'max-content' }} loading={loading} columns={columns} dataSource={rows} pagination={{ pageSize: 20 }} />
    </Card>
  )
}
