import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Form, Input, Popconfirm, Segmented, Select, Space, Table, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import CodeSelect from '../../common/adm/components/CodeSelect'
import PagePickerModal from '../../common/adm/components/PagePickerModal'
import BoardPickerModal from '../../common/adm/components/BoardPickerModal'
import MenuGlyph, { MENU_ICON_KEYS } from '../../common/adm/components/MenuGlyph'
import { runWithMessage } from '../../common/util/action'
import { menuApi } from './menu.api'
import type { Menu } from './menu.api'
import { pageApi } from '../page/page.api'
import { boardApi } from '../board/board.api'

/** 플랫 메뉴 → p_menu_id 기준 계층 트리(최상위=-1) */
function buildTree(list: Menu[]): Menu[] {
  const byParent = new Map<string, Menu[]>()
  for (const m of list) {
    const p = m.pMenuId ?? '0'
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p)!.push(m)
  }
  const build = (pid: string): Menu[] =>
    (byParent.get(pid) ?? []).map((m) => {
      const children = build(m.rowId!)
      return children.length ? { ...m, children } : { ...m }
    })
  return build('0')
}

function filterTree(nodes: Menu[], kw: string): Menu[] {
  const res: Menu[] = []
  for (const n of nodes) {
    const kids = n.children ? filterTree(n.children, kw) : []
    if ((n.menuName ?? '').includes(kw) || kids.length) res.push(kids.length ? { ...n, children: kids } : { ...n, children: undefined })
  }
  return res
}

function allKeys(nodes: Menu[]): string[] {
  return nodes.flatMap((n) => [n.rowId!, ...(n.children ? allKeys(n.children) : [])])
}

/**
 * 메뉴 관리 — 계층 트리 + 분할 마스터-디테일.
 * 좌: 트리(부모→펼치면 하위 메뉴, 같은 부모 내 ▲▼ 순서변경), 우: 상세·등록·수정.
 */
export default function MenuListPage() {
  const [area, setArea] = useState<'ADM' | 'GEN'>('ADM')
  const [flat, setFlat] = useState<Menu[]>([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [expandedRowKeys, setExpandedRowKeys] = useState<string[]>([])

  const reload = () => {
    setLoading(true)
    menuApi
      .manageTree(area)
      .then(setFlat)
      .catch((e) => message.error(e instanceof Error ? e.message : '목록 조회 실패'))
      .finally(() => setLoading(false))
  }
  useEffect(() => {
    reload()
  }, [area])

  const treeData = useMemo(() => {
    const full = buildTree(flat)
    return keyword ? filterTree(full, keyword) : full
  }, [flat, keyword])

  useEffect(() => {
    if (keyword) setExpandedRowKeys(allKeys(treeData))
  }, [keyword, treeData])

  const { form, mode, selectedKey, openNew, openRow, save, remove, reset } = useSplitForm<Menu>(menuApi, reload)
  const isEdit = mode === 'edit'
  const connCd = Form.useWatch('connCd', form)
  const [pagePickerOpen, setPagePickerOpen] = useState(false)
  const [postPickerOpen, setPostPickerOpen] = useState(false)
  const [connTitle, setConnTitle] = useState('') // 연결 대상(페이지/게시판) 표시용 이름

  const move = (row: Menu, dir: 'UP' | 'DOWN') =>
    runWithMessage(() => menuApi.moveSort(row.rowId!, dir), '순서를 변경했습니다.', reload)

  /** 신규 — 영역(area)은 현재 선택 탭으로 자동 세팅 (initialValues는 마운트 후 갱신 안 되므로 명시) */
  const openNewMenu = () => {
    openNew()
    form.setFieldsValue({ area })
    setConnTitle('')
  }

  /** 선택 메뉴 하위에 신규(상위 메뉴 미리 채움) */
  const addChild = () => {
    const parent = selectedKey
    openNew()
    form.setFieldsValue({ area, ...(parent ? { pMenuId: parent } : {}) })
    setConnTitle('')
  }

  /** 행 선택(수정) — 연결유형이 페이지/게시판이면 연결 대상 이름도 로드 */
  const openRowMenu = async (rowId: string) => {
    await openRow(rowId)
    const cty = form.getFieldValue('connCd')
    const cid = form.getFieldValue('connId')
    setConnTitle('')
    if (!cid) return
    try {
      if (cty === 'MENU03') setConnTitle((await pageApi.view(String(cid))).title ?? '')
      else if (cty === 'MENU02') setConnTitle((await boardApi.view(String(cid))).boardName ?? '')
    } catch {
      setConnTitle('')
    }
  }

  const columns: TableColumnsType<Menu> = [
    { title: '메뉴명', dataIndex: 'menuName' },
    { title: '메뉴ID', dataIndex: 'rowId', width: 80 },
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
    <Card title="메뉴 트리">
      <SearchBar
        leftExtra={
          <Segmented
            size="large"
            value={area}
            onChange={(v) => { setArea(v as 'ADM' | 'GEN'); reset() }}
            options={[{ label: '관리자', value: 'ADM' }, { label: '사용자', value: 'GEN' }]}
          />
        }
        fields={[{ type: 'text', name: 'filterKeyword', placeholder: '메뉴명', width: 260 }]}
        onSearch={(v) => setKeyword(v.filterKeyword ?? '')}
      />
      <Table<Menu>
        rowKey="rowId"
        scroll={{ x: 'max-content' }}
        size="small"
        columns={columns}
        dataSource={treeData}
        loading={loading}
        pagination={false}
        expandable={{ expandedRowKeys, onExpandedRowsChange: (keys) => setExpandedRowKeys(keys as string[]) }}
        rowClassName={(r) => (r.rowId === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRowMenu(r.rowId!), style: { cursor: 'pointer' } })}
      />
    </Card>
  )

  const detail = (
    <Card
      title="상세 / 등록 / 수정"
      extra={
        <Space>
          <Button onClick={addChild} disabled={!selectedKey}>하위메뉴추가</Button>
          <Button onClick={openNewMenu}>신규</Button>
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
        <Form form={form} layout="vertical" initialValues={{ area, pMenuId: '0', connCd: 'MENU04', targetYn: 'N' }}>
          <Form.Item name="area" label="영역(현재 탭 기준)">
            <Select disabled options={[{ value: 'ADM', label: '관리자' }, { value: 'GEN', label: '사용자' }]} />
          </Form.Item>
          <Form.Item name="menuName" label="메뉴명" rules={[{ required: true, message: '메뉴명을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="pMenuId" label="상위 메뉴 ID (수정 불가 — 배치는 하위메뉴추가/신규로)" rules={[{ required: true, message: '상위 메뉴 ID(최상위 0)' }]}>
            <Input disabled={isEdit} />
          </Form.Item>
          <Form.Item name="connCd" label="연결유형" rules={[{ required: true, message: '연결유형을 선택하세요.' }]}>
            <CodeSelect pCodeId="MENU00" placeholder="연결유형 선택" />
          </Form.Item>
          {connCd === 'MENU01' && (
            <Form.Item name="linkUrl" label="URL/라우트" rules={[{ required: true, message: '예: /adm/code' }]}>
              <Input placeholder="/adm/code" />
            </Form.Item>
          )}
          {connCd === 'MENU02' && (
            <Form.Item label="연결 게시판" required>
              <Space>
                <Form.Item name="connId" noStyle rules={[{ required: true, message: '게시판을 선택하세요.' }]}>
                  <Input readOnly placeholder="게시판 선택 →" style={{ width: 130 }} />
                </Form.Item>
                {connTitle && <span style={{ color: '#555' }}>{connTitle}</span>}
                <Button onClick={() => setPostPickerOpen(true)}>게시판 선택</Button>
              </Space>
            </Form.Item>
          )}
          {connCd === 'MENU03' && (
            <Form.Item label="연결 페이지" required>
              <Space>
                <Form.Item name="connId" noStyle rules={[{ required: true, message: '페이지를 선택하세요.' }]}>
                  <Input readOnly placeholder="페이지 선택 →" style={{ width: 130 }} />
                </Form.Item>
                {connTitle && <span style={{ color: '#555' }}>{connTitle}</span>}
                <Button onClick={() => setPagePickerOpen(true)}>페이지 선택</Button>
              </Space>
            </Form.Item>
          )}
          {isEdit && (
            <Form.Item name="sortNo" label="정렬순서(순서변경은 목록의 ▲▼)">
              <Input disabled />
            </Form.Item>
          )}
          <Form.Item name="icon" label="아이콘 (사이드바 표시, 선택)">
            <Select
              allowClear
              placeholder="아이콘 선택"
              optionLabelProp="label"
              options={MENU_ICON_KEYS.map((k) => ({
                value: k,
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <MenuGlyph name={k} size={16} />
                    {k}
                  </span>
                ),
              }))}
            />
          </Form.Item>
          <Form.Item name="description" label="설명">
            <Input />
          </Form.Item>
          <Form.Item name="targetYn" label="새창 여부(Y/N)">
            <Input />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="메뉴 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
      <PagePickerModal
        open={pagePickerOpen}
        onClose={() => setPagePickerOpen(false)}
        onSelect={(p) => {
          form.setFieldsValue({ connId: p.rowId })
          setConnTitle(p.title ?? '')
        }}
      />
      <BoardPickerModal
        open={postPickerOpen}
        onClose={() => setPostPickerOpen(false)}
        onSelect={(b) => {
          form.setFieldsValue({ connId: b.rowId })
          setConnTitle(b.boardName ?? '')
        }}
      />
    </Card>
  )
}
