import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { reportApi } from '../../api/report'
import type { Report } from '../../api/report'

const TYPE_NM: Record<string, string> = { BBS: '게시글', COMMENT: '댓글', RECRUIT: '모집' }
const statusTag = (s?: string) =>
  s === 'RESOLVED' ? <Tag color="red">삭제조치</Tag>
    : s === 'DISMISSED' ? <Tag>반려</Tag>
      : <Tag color="orange">대기</Tag>

/** 신고 관리(관리자) — 게시글/댓글/모집 신고 목록 + 처리(삭제조치=대상 숨김 / 반려 / 대기로 복원). */
export default function ReportListPage() {
  const [rows, setRows] = useState<Report[]>([])
  const [status, setStatus] = useState<string>('') // 기본 전체
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [totCnt, setTotCnt] = useState(0)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const r = await reportApi.list(status, page, size)
      setRows(r.list ?? [])
      setTotCnt(r.totCnt ?? 0)
      // 처리/필터로 건수가 줄어 현재 페이지가 비면 이전 페이지로 (빈 화면 방지)
      if ((r.list ?? []).length === 0 && page > 1) {
        setPage(page - 1)
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '목록 조회 실패')
    } finally {
      setLoading(false)
    }
  }, [status, page, size])

  useEffect(() => { load() }, [load])

  const handle = async (rowId: string, next: string) => {
    try {
      await reportApi.updateStatus(rowId, next)
      const msg = next === 'RESOLVED' ? '삭제조치했습니다. (대상을 숨김 처리)'
        : next === 'DISMISSED' ? '반려했습니다.'
          : '대기로 되돌렸습니다. (대상 복원)'
      message.success(msg)
      load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '처리 실패')
    }
  }

  const columns: TableColumnsType<Report> = [
    { title: '유형', width: 80, align: 'center', render: (_, r) => TYPE_NM[r.targetType ?? ''] ?? r.targetType },
    {
      title: '대상',
      render: (_, r) => {
        const label = r.targetTitle || `#${r.targetId}`
        return r.linkUrl
          ? <a href={r.linkUrl} target="_blank" rel="noreferrer" title="새 탭에서 대상 열기">{label}</a>
          : <span style={{ color: '#999' }}>{label} (삭제됨)</span>
      },
    },
    { title: '분류', width: 110, align: 'center', render: (_, r) => (r.reasonNm ? <Tag color="volcano">{r.reasonNm}</Tag> : '-') },
    { title: '내용', render: (_, r) => r.reason, ellipsis: true },
    { title: '신고자', width: 120, render: (_, r) => r.regNm || '-' },
    { title: '신고일', dataIndex: 'regDt', width: 140 },
    { title: '상태', width: 90, align: 'center', render: (_, r) => statusTag(r.status) },
    {
      title: '처리', width: 170, align: 'center',
      render: (_, r) =>
        r.status === 'PENDING' ? (
          <Space>
            <Popconfirm title="신고 대상을 삭제(숨김) 처리하시겠습니까?" onConfirm={() => handle(r.rowId!, 'RESOLVED')} okText="삭제조치" okButtonProps={{ danger: true }} cancelText="취소">
              <Button size="small" danger>삭제조치</Button>
            </Popconfirm>
            <Popconfirm title="반려(오신고) 처리하시겠습니까?" onConfirm={() => handle(r.rowId!, 'DISMISSED')} okText="반려" cancelText="취소">
              <Button size="small">반려</Button>
            </Popconfirm>
          </Space>
        ) : (
          <Popconfirm title="대기로 되돌립니다. (삭제조치였다면 대상이 다시 노출됩니다)" onConfirm={() => handle(r.rowId!, 'PENDING')} okText="되돌리기" cancelText="취소">
            <a style={{ fontSize: 12 }}>대기로</a>
          </Popconfirm>
        ),
    },
  ]

  return (
    <Card title="신고 관리">
      <Space style={{ marginBottom: 12 }}>
        <Select
          value={status} style={{ width: 140 }}
          onChange={(v) => { setStatus(v); setPage(1) }} // 필터 변경 시 첫 페이지로
          options={[
            { value: '', label: '전체' },
            { value: 'PENDING', label: '대기' },
            { value: 'RESOLVED', label: '삭제조치' },
            { value: 'DISMISSED', label: '반려' },
          ]}
        />
        <Button onClick={load}>새로고침</Button>
      </Space>
      <Table<Report>
        rowKey="rowId" size="small" scroll={{ x: 'max-content' }} loading={loading}
        columns={columns} dataSource={rows}
        pagination={{
          current: page, pageSize: size, total: totCnt,
          showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100],
          showTotal: (t) => `총 ${t}건`,
          onChange: (p, ps) => { setPage(p); setSize(ps) },
        }}
      />
    </Card>
  )
}
