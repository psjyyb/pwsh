import { useEffect } from 'react'
import { Button, Card, Form, Input, Popconfirm, Space, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import CodeSelect from '../../common/adm/components/CodeSelect'
import NumberInput from '../../common/adm/components/NumberInput'
import YnSelect from '../../common/adm/components/YnSelect'
import { BBSINFO_LIST_URL, bbsinfoApi } from './bbsinfo.api'
import type { Bbsinfo } from './bbsinfo.api'

/** 게시판관리 — 게시판 정의(설정) 분할 마스터-디테일 CRUD. */
export default function BbsinfoListPage() {
  const { rows, total, loading, page, size, reload, search, changePage } = useList<Bbsinfo>(BBSINFO_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<Bbsinfo>(bbsinfoApi, reload)
  const isEdit = mode === 'edit'
  const fileYnWatch = Form.useWatch('fileYn', form) // 첨부 사용여부 → 개수/용량 입력 노출 제어
  const cdWatch = Form.useWatch('bbsinfoCd', form)
  const isFaqType = cdWatch === 'BBSINFO002' // FAQ는 첨부 개념 자체가 없음

  // FAQ 유형 선택 시 첨부 사용을 강제로 N (첨부 설정 숨김과 일관)
  useEffect(() => {
    if (isFaqType) form.setFieldsValue({ fileYn: 'N' })
  }, [isFaqType, form])

  const columns: TableColumnsType<Bbsinfo> = [
    { title: '게시판명', dataIndex: 'bbsinfoNm' },
    { title: '유형', width: 120, render: (_, r) => r.bbsinfoCdNm ?? r.bbsinfoCd },
    { title: '사용', dataIndex: 'useYn', width: 60 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[{ type: 'text', name: 'searchKeyword', placeholder: '게시판명', width: 260 }]}
        onSearch={(v) => search(v)}
      />
      <Table<Bbsinfo>
        rowKey="rowId"
        scroll={{ x: 'max-content' }}
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r.rowId!), style: { cursor: 'pointer' } })}
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
        <Form
          form={form}
          layout="vertical"
          initialValues={{ listCnt: '10', fileYn: 'N', fileCnt: '5', fileSize: '10', noticeYn: 'N', newCnt: '0' }}
        >
          <Form.Item name="bbsinfoNm" label="게시판명" rules={[{ required: true, message: '게시판명을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="bbsinfoCd" label="게시판 유형" rules={[{ required: true, message: '유형을 선택하세요.' }]}>
            <CodeSelect pCodeId="BBSINFO000" placeholder="유형 선택" />
          </Form.Item>
          <Form.Item name="bbsinfoDesc" label="설명">
            <Input />
          </Form.Item>
          <div>
            <Space size={8} wrap>
              <Form.Item name="listCnt" label="목록당 글 수"><NumberInput /></Form.Item>
              <Form.Item name="newCnt" label="NEW 표시 일수"><NumberInput /></Form.Item>
              <Form.Item name="noticeYn" label="공지 사용"><YnSelect /></Form.Item>
            </Space>
          </div>
          {!isFaqType && (
            <div>
              <Space size={8} wrap align="baseline">
                <Form.Item name="fileYn" label="첨부 사용"><YnSelect /></Form.Item>
                {fileYnWatch === 'Y' && <Form.Item name="fileCnt" label="첨부 개수"><NumberInput /></Form.Item>}
                {fileYnWatch === 'Y' && <Form.Item name="fileSize" label="첨부 용량(MB)"><NumberInput /></Form.Item>}
              </Space>
            </div>
          )}
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="게시판관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
