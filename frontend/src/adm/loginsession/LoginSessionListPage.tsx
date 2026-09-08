import { useState } from 'react'
import { Button, Card, Popconfirm, Table, Tag, Tooltip, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import { LOGINSESSION_LIST_URL, loginSessionApi } from './loginsession.api'
import type { LoginSession } from './loginsession.api'

/** end_reason 코드 → 표시 문구. 서버 상수(LoginSessionService.END_*)와 짝을 맞춘다. */
const END_REASON_LABEL: Record<string, string> = {
  LOGOUT: '로그아웃',
  FORCE: '관리자 강제종료',
  RELOGIN: '다른 기기 로그인',
  PWCHANGE: '비밀번호 변경',
}

/**
 * 접속 세션 — 지금 누가 접속 중인지 보고, 필요하면 강제 종료한다.
 * 목록은 기본으로 '접속중'만 보여준다(전체를 보면 지난 접속 이력까지 섞여 현황 파악이 어렵다).
 */
export default function LoginSessionListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<LoginSession>(
    LOGINSESSION_LIST_URL,
    { filterStatus: 'ACTIVE' },
  )
  const [ending, setEnding] = useState<string | null>(null)

  const forceEnd = async (row: LoginSession) => {
    setEnding(row.rowId!)
    try {
      await loginSessionApi.forceEnd(row.rowId!)
      message.success(`${row.memberId} 세션을 종료했습니다.`)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '종료에 실패했습니다.')
    } finally {
      setEnding(null)
    }
  }

  const columns: TableColumnsType<LoginSession> = [
    {
      title: '상태',
      dataIndex: 'activeYn',
      width: 90,
      render: (v: string) =>
        v === 'Y' ? <Tag color="green">접속중</Tag> : <Tag>종료</Tag>,
    },
    { title: '아이디', dataIndex: 'memberId', width: 120 },
    { title: '이름', dataIndex: 'memberName', width: 100 },
    { title: 'IP', dataIndex: 'regIp', width: 130 },
    {
      title: '기기',
      dataIndex: 'deviceType',
      width: 90,
      render: (v: string, r) => (
        // User-Agent 원문은 길어서 컬럼으로 두면 표가 무너진다 → 툴팁으로 확인
        <Tooltip title={r.userAgent || '-'}>
          <span>{v || '-'}</span>
        </Tooltip>
      ),
    },
    { title: '로그인', dataIndex: 'loginDt', width: 170 },
    { title: '마지막 활동', dataIndex: 'lastSeenDt', width: 170 },
    {
      title: '종료',
      dataIndex: 'endDt',
      width: 170,
      render: (v: string, r) =>
        v ? `${v} (${END_REASON_LABEL[r.endReason ?? ''] ?? r.endReason})` : '-',
    },
    {
      title: '',
      width: 100,
      render: (_, r) =>
        r.activeYn === 'Y' ? (
          <Popconfirm
            title="세션을 종료하시겠습니까?"
            description="해당 사용자의 토큰이 즉시 무효화되어 다시 로그인해야 합니다."
            onConfirm={() => forceEnd(r)}
            okText="종료"
            cancelText="취소"
          >
            <Button size="small" danger loading={ending === r.rowId}>
              강제종료
            </Button>
          </Popconfirm>
        ) : null,
    },
  ]

  return (
    <Card title="접속 세션" styles={{ body: { padding: 12 } }}>
      <SearchBar
        fields={[
          {
            type: 'select',
            name: 'filterStatus',
            placeholder: '상태',
            width: 130,
            defaultValue: 'ACTIVE', // useList의 initialParams와 같은 값 — 표시와 결과를 맞춘다
            options: [
              { value: 'ACTIVE', label: '접속중' },
              { value: 'ENDED', label: '종료' },
              { value: '', label: '전체' },
            ],
          },
          { type: 'text', name: 'filterKeyword', placeholder: '아이디', width: 200 },
        ]}
        onSearch={(v) => search(v)}
      />
      <Table<LoginSession>
        rowKey="rowId"
        size="small"
        scroll={{ x: 1100 }}
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{
          current: page,
          pageSize: pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, ps) => changePage(p, ps),
        }}
      />
    </Card>
  )
}
