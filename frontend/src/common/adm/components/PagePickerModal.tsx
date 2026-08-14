import { Button, Modal, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../hooks/useList'
import SearchBar from './SearchBar'
import { PAGE_LIST_URL } from '../../../adm/page/page.api'
import type { Page } from '../../../adm/page/page.api'

interface Props {
  open: boolean
  onSelect: (page: Page) => void
  onClose: () => void
}

/**
 * 일반페이지 선택 팝업 — 제목으로 검색해 한 건 선택. (메뉴 연결유형=페이지 등에서 재사용)
 * 선택 시 onSelect(page)로 전체 정보를 넘김(연결에는 page.rowId=page_id 사용).
 */
export default function PagePickerModal({ open, onSelect, onClose }: Props) {
  const { rows, total, loading, page, size, search, changePage } = useList<Page>(PAGE_LIST_URL)

  const columns: TableColumnsType<Page> = [
    { title: '페이지ID', dataIndex: 'rowId', width: 90 },
    { title: '제목', dataIndex: 'title' },
    {
      title: '',
      width: 80,
      render: (_, r) => (
        <Button type="primary" size="small" onClick={() => { onSelect(r); onClose() }}>
          선택
        </Button>
      ),
    },
  ]

  return (
    <Modal open={open} onCancel={onClose} footer={null} title="페이지 선택" width={640} destroyOnHidden>
      <SearchBar
        fields={[{ type: 'text', name: 'searchKeyword', placeholder: '제목', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<Page>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        onRow={(r) => ({ onClick: () => { onSelect(r); onClose() }, style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: size, total, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Modal>
  )
}
