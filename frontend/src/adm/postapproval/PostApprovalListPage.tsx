import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Input, Popconfirm, Space, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import SafeHtml from '../../common/SafeHtml'
import { boardApi } from '../board/board.api'
import { POST_APPROVAL_LIST_URL, postApprovalApi } from './postapproval.api'
import type { PostApproval } from './postapproval.api'

const STATUS_OPTIONS = [
  { value: 'APPROVAL01', label: '승인 대기' },
  { value: 'APPROVAL03', label: '반려' },
  { value: 'APPROVAL02', label: '승인' },
]
const STATUS_COLOR: Record<string, string> = { APPROVAL01: 'gold', APPROVAL02: 'green', APPROVAL03: 'red' }
const MEMO_MAX = 500

/**
 * 게시 승인(CMS 1.2.41) — 게시 승인을 켠 게시판(게시판 설정 > 게시 승인)의 회원 글을 모든 게시판에서 모아 승인·반려한다.
 * 승인 전 글은 작성자·관리자만 본다. 반려는 사유 필수이고 작성자에게 알림이 간다 — 작성자가 고치면 다시 대기로 들어온다.
 * 목록은 기본이 '승인 대기'이고 오래 기다린 글부터 나온다.
 */
export default function PostApprovalListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } =
    useList<PostApproval>(POST_APPROVAL_LIST_URL, { approvalCd: 'APPROVAL01' })
  const [boards, setBoards] = useState<{ value: string; label: string }[]>([])
  const [selected, setSelected] = useState<PostApproval | null>(null)
  const [memo, setMemo] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    boardApi.comboList()
      .then((list) => setBoards(list.map((b) => ({ value: b.rowId!, label: b.boardName ?? b.rowId! }))))
      .catch(() => setBoards([]))
  }, [])

  const open = async (rowId: string) => {
    try {
      setSelected(await postApprovalApi.view(rowId))
      setMemo('')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회에 실패했습니다.')
    }
  }

  const decide = async (approvalCd: 'APPROVAL02' | 'APPROVAL03') => {
    if (!selected?.rowId) return
    setBusy(true)
    try {
      await postApprovalApi.decide(selected.rowId, approvalCd, approvalCd === 'APPROVAL03' ? memo.trim() : undefined)
      message.success(approvalCd === 'APPROVAL02' ? '승인했습니다. 작성자에게 알림을 보냈습니다.' : '반려했습니다. 작성자에게 사유를 알렸습니다.')
      setSelected(null)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '처리에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const columns: TableColumnsType<PostApproval> = [
    { title: '게시판', dataIndex: 'boardName', width: 120 },
    {
      title: '제목',
      render: (_, r) => (
        <span>
          {r.replyYn === 'Y' && <Tag>답글</Tag>}
          {r.title}
          {r.resubmitYn === 'Y' && <Tag color="blue" style={{ marginLeft: 6 }}>고쳐서 다시 요청</Tag>}
        </span>
      ),
    },
    { title: '작성자', width: 140, render: (_, r) => `${r.regName ?? '-'} (${r.regId ?? '-'})` },
    { title: '상태', width: 90, render: (_, r) => <Tag color={STATUS_COLOR[r.approvalCd ?? '']}>{r.approvalName ?? r.approvalCd}</Tag> },
    { title: '올린 시각', dataIndex: 'updDt', width: 140 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[
          { type: 'select', name: 'approvalCd', placeholder: '상태', options: STATUS_OPTIONS, defaultValue: 'APPROVAL01', width: 130 },
          { type: 'select', name: 'boardId', placeholder: '게시판', options: boards, width: 160 },
          { type: 'text', name: 'filterKeyword', placeholder: '제목', width: 200 },
        ]}
        onSearch={(v) => { setSelected(null); search(v) }}
      />
      <Table<PostApproval>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selected?.rowId ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => open(r.rowId!), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const waiting = selected?.approvalCd === 'APPROVAL01'
  const detail = (
    <Card
      title="내용 확인 / 처리"
      extra={
        waiting && (
          <Space>
            <Popconfirm title="승인하면 모두에게 공개됩니다. 승인할까요?" onConfirm={() => decide('APPROVAL02')} okText="승인" cancelText="취소">
              <Button type="primary" loading={busy}>승인</Button>
            </Popconfirm>
            <Popconfirm
              title="반려할까요? 작성자에게 사유가 전달됩니다."
              onConfirm={() => decide('APPROVAL03')}
              okText="반려"
              cancelText="취소"
              disabled={!memo.trim()}
            >
              <Button danger loading={busy} disabled={!memo.trim()}>반려</Button>
            </Popconfirm>
          </Space>
        )
      }
    >
      {!selected ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>목록에서 글을 고르세요.</div>
      ) : (
        <>
          <Descriptions size="small" column={1} bordered style={{ marginBottom: 12 }}>
            <Descriptions.Item label="게시판">{selected.boardName}{selected.replyYn === 'Y' && ' (답글)'}</Descriptions.Item>
            <Descriptions.Item label="작성자">{selected.regName} ({selected.regId})</Descriptions.Item>
            <Descriptions.Item label="올린 시각">{selected.updDt} (처음 작성 {selected.regDt})</Descriptions.Item>
            <Descriptions.Item label="상태">
              <Tag color={STATUS_COLOR[selected.approvalCd ?? '']}>{selected.approvalName}</Tag>
              {selected.approvalDt && <span style={{ color: '#888' }}>마지막 처리 {selected.approvalDt} · {selected.approvalId}</span>}
            </Descriptions.Item>
            {selected.approvalMemo && <Descriptions.Item label="반려 사유">{selected.approvalMemo}</Descriptions.Item>}
          </Descriptions>
          <h3 style={{ margin: '4px 0 8px' }}>{selected.title}</h3>
          {/* 본문은 사용자가 쓴 HTML이다 — SafeHtml로만 그린다(innerHTML 직접 금지) */}
          <SafeHtml className="toastui-editor-contents" style={{ minHeight: 120, marginBottom: 16 }} html={selected.content ?? ''} />
          {waiting && (
            <Input.TextArea
              rows={3}
              maxLength={MEMO_MAX}
              showCount
              placeholder="반려 사유(반려할 때 필수 — 작성자에게 그대로 전달됩니다)"
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
            />
          )}
        </>
      )}
    </Card>
  )

  return (
    <Card title="게시 승인" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
