import { Button, Card, Form, Input, Popconfirm, Space, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import DateField from '../../common/adm/components/DateField'
import ImageUpload from '../../common/adm/components/ImageUpload'
import { runWithMessage } from '../../common/util/action'
import { POPUP_LIST_URL, popupApi } from './popup.api'
import type { Popup } from './popup.api'

/** 팝업 관리 — 분할 마스터-디테일(평면). */
export default function PopupListPage() {
  const { rows, total, loading, page, size, reload, search, changePage } = useList<Popup>(POPUP_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<Popup>(popupApi, reload)
  const isEdit = mode === 'edit'

  const move = (row: Popup, dir: 'UP' | 'DOWN') =>
    runWithMessage(() => popupApi.moveOrdr(row.dbKey!, dir), '순서를 변경했습니다.', reload)

  const columns: TableColumnsType<Popup> = [
    { title: '팝업명', dataIndex: 'popNm' },
    { title: '사용', dataIndex: 'useYn', width: 60 },
    {
      title: '순서',
      width: 100,
      render: (_, row) => (
        <Space size={4} onClick={(e) => e.stopPropagation()}>
          <span>{row.ordr}</span>
          <Button size="small" onClick={() => move(row, 'UP')}>▲</Button>
          <Button size="small" onClick={() => move(row, 'DOWN')}>▼</Button>
        </Space>
      ),
    },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'searchKeyword', placeholder: '팝업명', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<Popup>
        rowKey="dbKey"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.dbKey === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r.dbKey!), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: size, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const detail = (
    <Card
      title="상세 / 등록 / 수정"
      extra={
        <Space>
          <Button onClick={openNew}>신규</Button>
          <Button type="primary" onClick={save} disabled={mode === 'none'}>저장</Button>
          <Popconfirm title="삭제하시겠습니까?" onConfirm={remove} okText="삭제" cancelText="취소" disabled={!isEdit}>
            <Button danger disabled={!isEdit}>삭제</Button>
          </Popconfirm>
        </Space>
      }
    >
      {mode === 'none' ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>행을 선택하거나 [신규]를 누르세요.</div>
      ) : (
        <Form form={form} layout="vertical">
          <Form.Item name="popNm" label="팝업명" rules={[{ required: true, message: '팝업명을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="fileId" label="이미지">
            <ImageUpload />
          </Form.Item>
          <Form.Item name="startDt" label="노출 시작일">
            <DateField allowClear placeholder="시작일 선택" />
          </Form.Item>
          <Form.Item name="endDt" label="노출 종료일">
            <DateField allowClear placeholder="종료일 선택" />
          </Form.Item>
          {isEdit && (
            <Form.Item name="ordr" label="정렬순서(순서변경은 목록의 ▲▼)">
              <Input disabled />
            </Form.Item>
          )}
          <Form.Item name="link" label="연결 링크">
            <Input />
          </Form.Item>
          <Form.Item name="txt" label="내용">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Space size={8} wrap>
            <Form.Item name="popWidth" label="너비(px)">
              <Input style={{ width: 110 }} placeholder="예: 400" />
            </Form.Item>
            <Form.Item name="popHeight" label="높이(px)">
              <Input style={{ width: 110 }} placeholder="예: 300" />
            </Form.Item>
            <Form.Item name="popTop" label="위치 top(px)">
              <Input style={{ width: 110 }} placeholder="예: 100" />
            </Form.Item>
            <Form.Item name="popLeft" label="위치 left(px)">
              <Input style={{ width: 110 }} placeholder="예: 100" />
            </Form.Item>
          </Space>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="팝업 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
