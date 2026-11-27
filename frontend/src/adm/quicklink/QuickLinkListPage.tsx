import { Button, Card, Form, Input, Popconfirm, Select, Space, Table, Tag } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import IconPicker from '../../common/adm/components/IconPicker'
import MenuGlyph from '../../common/adm/components/MenuGlyph'
import { runWithMessage } from '../../common/util/action'
import { QUICK_LINK_LIST_URL, isSafeQuickLinkUrl, quickLinkApi } from './quicklink.api'
import type { QuickLink } from './quicklink.api'

const TARGET_OPTIONS = [
  { value: 'N', label: '현재 창' },
  { value: 'Y', label: '새 창' },
]
const DISPLAY_OPTIONS = [
  { value: 'Y', label: '노출' },
  { value: 'N', label: '숨김(메인에서만 빠지고 순서 자리는 지킨다)' },
]

/**
 * 자주찾는 서비스(CMS 1.2.45) — 사용자 메인 히어로 바로 아래의 바로가기 버튼. 분할 마스터-디테일(배너 관리와 같은 모양).
 * 순서는 목록의 ▲▼로만 바꾼다(저장은 순서를 건드리지 않는다). 하나도 노출되지 않으면 메인에 이 영역이 나오지 않는다.
 */
export default function QuickLinkListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<QuickLink>(QUICK_LINK_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<QuickLink>(quickLinkApi, reload)
  const isEdit = mode === 'edit'

  const move = (row: QuickLink, dir: 'UP' | 'DOWN') =>
    runWithMessage(() => quickLinkApi.moveSort(row.rowId!, dir), '순서를 변경했습니다.', reload)

  const startNew = () => {
    openNew()
    form.setFieldsValue({ targetYn: 'N', displayYn: 'Y' })
  }

  const columns: TableColumnsType<QuickLink> = [
    {
      title: '이름',
      dataIndex: 'name',
      render: (v: string, row) => (
        <Space size={6}>
          <MenuGlyph name={row.icon} size={16} />
          <span>{v}</span>
          {row.displayYn === 'N' && <Tag>숨김</Tag>}
          {row.targetYn === 'Y' && <Tag color="blue">새 창</Tag>}
        </Space>
      ),
    },
    { title: '주소', dataIndex: 'linkUrl', ellipsis: true },
    {
      title: '순서',
      width: 100,
      render: (_, row) => (
        <Space size={4} onClick={(e) => e.stopPropagation()}>
          <span>{row.sortNo}</span>
          <Button size="small" onClick={() => move(row, 'UP')}>▲</Button>
          <Button size="small" onClick={() => move(row, 'DOWN')}>▼</Button>
        </Space>
      ),
    },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: '이름·주소', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<QuickLink>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r.rowId!), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const detail = (
    <Card
      title="상세 / 등록 / 수정"
      extra={
        <Space>
          <Button onClick={startNew}>신규</Button>
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
          <Form.Item
            name="name"
            label="이름"
            rules={[{ required: true, whitespace: true, message: '이름을 입력하세요.' }, { max: 50, message: '50자까지입니다.' }]}
          >
            <Input maxLength={50} placeholder="예: 민원 신청" />
          </Form.Item>
          <Form.Item
            name="linkUrl"
            label="주소"
            extra="사이트 안이면 /로 시작하는 경로(예: /gen/board/1), 밖이면 https://로 시작하는 전체 주소."
            rules={[
              { required: true, whitespace: true, message: '주소를 입력하세요.' },
              {
                validator: (_, v?: string) =>
                  !v || isSafeQuickLinkUrl(v.trim())
                    ? Promise.resolve()
                    : Promise.reject(new Error('/로 시작하는 경로나 http(s)://로 시작하는 주소만 쓸 수 있습니다.')),
              },
            ]}
          >
            <Input maxLength={255} placeholder="/gen/board/1" />
          </Form.Item>
          <Form.Item name="icon" label="아이콘 (선택 — 비우면 기본 그림)">
            <IconPicker />
          </Form.Item>
          <Space size={8} wrap>
            <Form.Item name="targetYn" label="열기">
              <Select style={{ width: 140 }} options={TARGET_OPTIONS} />
            </Form.Item>
            <Form.Item name="displayYn" label="메인 노출">
              <Select style={{ width: 320 }} options={DISPLAY_OPTIONS} />
            </Form.Item>
          </Space>
          {isEdit && (
            <Form.Item name="sortNo" label="정렬순서(순서변경은 목록의 ▲▼)">
              <Input disabled />
            </Form.Item>
          )}
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="자주찾는 서비스" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
