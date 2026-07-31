import { useEffect, useState } from 'react'
import { Button, Modal, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { bbsinfoApi } from '../../../adm/bbsinfo/bbsinfo.api'
import type { Bbsinfo } from '../../../adm/bbsinfo/bbsinfo.api'

interface Props {
  open: boolean
  onSelect: (bbsinfo: Bbsinfo) => void
  onClose: () => void
}

/** 게시판 선택 팝업 — 메뉴 연결유형=게시판에서 bbsinfo_id 선택. */
export default function BbsinfoPickerModal({ open, onSelect, onClose }: Props) {
  const [rows, setRows] = useState<Bbsinfo[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    bbsinfoApi.comboList().then(setRows).catch(() => setRows([])).finally(() => setLoading(false))
  }, [open])

  const columns: TableColumnsType<Bbsinfo> = [
    { title: '게시판ID', dataIndex: 'dbKey', width: 90 },
    { title: '게시판명', dataIndex: 'bbsinfoNm' },
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
      <Table<Bbsinfo>
        rowKey="dbKey"
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
