import { useEffect, useState } from 'react'
import { Button, Modal, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { boardApi } from '../../../adm/board/board.api'
import type { Board } from '../../../adm/board/board.api'

interface Props {
  open: boolean
  onSelect: (board: Board) => void
  onClose: () => void
}

/** 게시판 선택 팝업 — 메뉴 연결유형=게시판에서 board_id 선택. */
export default function BoardPickerModal({ open, onSelect, onClose }: Props) {
  const [rows, setRows] = useState<Board[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    boardApi.comboList().then(setRows).catch(() => setRows([])).finally(() => setLoading(false))
  }, [open])

  const columns: TableColumnsType<Board> = [
    { title: '게시판ID', dataIndex: 'rowId', width: 90 },
    { title: '게시판명', dataIndex: 'boardName' },
    {
      title: '',
      width: 80,
      render: (_, r) => (
        <Button type="primary" size="small" onClick={() => { onSelect(r); onClose() }}>선택</Button>
      ),
    },
  ]

  return (
    <Modal open={open} onCancel={onClose} footer={null} title="게시판 선택" width={560} destroyOnHidden>
      <Table<Board>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={false}
        onRow={(r) => ({ onClick: () => { onSelect(r); onClose() }, style: { cursor: 'pointer' } })}
      />
    </Modal>
  )
}
