import { Button, Card, Form, Input, Popconfirm, Space, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import DateField from '../../common/adm/components/DateField'
import ImageUpload from '../../common/adm/components/ImageUpload'
import { runWithMessage } from '../../common/util/action'
import { BANNER_LIST_URL, bannerApi } from './banner.api'
import type { Banner } from './banner.api'

/** 목록에서 제목을 한 줄로 보이기 위한 정리 — 강조 마커를 벗기고 줄바꿈을 공백으로 */
const plainTitle = (t?: string) => (t ?? '').replace(/\[\[|\]\]/g, '').replace(/\s*\n\s*/g, ' ')

/** 배너 관리 — 메인 히어로 슬라이드. 분할 마스터-디테일(평면). */
export default function BannerListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<Banner>(BANNER_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<Banner>(bannerApi, reload)
  const isEdit = mode === 'edit'

  const move = (row: Banner, dir: 'UP' | 'DOWN') =>
    runWithMessage(() => bannerApi.moveSort(row.rowId!, dir), '순서를 변경했습니다.', reload)

  const columns: TableColumnsType<Banner> = [
    { title: '제목', dataIndex: 'title', render: (v: string) => plainTitle(v) },
    { title: '노출기간', width: 190, render: (_, row) => `${row.startDt || '제한없음'} ~ ${row.endDt || '제한없음'}` },
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
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: '제목', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<Banner>
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
          <Form.Item
            name="title"
            label="제목"
            extra="줄바꿈은 그대로 표시됩니다. 강조할 구간은 [[대괄호 두 개]]로 감싸세요. (HTML 태그는 그대로 글자로 보입니다)"
            rules={[{ required: true, message: '제목을 입력하세요.' }]}
          >
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="description" label="설명" extra="줄바꿈은 그대로 표시됩니다.">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="fileId" label="배경 이미지" extra="비우면 기본 그라디언트 배경이 나옵니다.">
            <ImageUpload />
          </Form.Item>
          <Space size={8} wrap>
            <Form.Item name="btn1Label" label="버튼1 문구">
              <Input style={{ width: 200 }} placeholder="비우면 버튼 없음" />
            </Form.Item>
            <Form.Item name="btn1Url" label="버튼1 링크">
              <Input style={{ width: 240 }} placeholder="예: /gen/board/1" />
            </Form.Item>
          </Space>
          <Space size={8} wrap>
            <Form.Item name="btn2Label" label="버튼2 문구">
              <Input style={{ width: 200 }} placeholder="비우면 버튼 없음" />
            </Form.Item>
            <Form.Item name="btn2Url" label="버튼2 링크">
              <Input style={{ width: 240 }} placeholder="예: /gen/board/2" />
            </Form.Item>
          </Space>
          <Form.Item name="startDt" label="노출 시작일">
            <DateField allowClear placeholder="시작일 선택" />
          </Form.Item>
          <Form.Item name="endDt" label="노출 종료일">
            <DateField allowClear placeholder="종료일 선택" />
          </Form.Item>
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
    <Card title="배너 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
