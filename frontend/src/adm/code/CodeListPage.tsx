import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Form, Input, Popconfirm, Space, Table, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { runWithMessage } from '../../common/util/action'
import { codeApi } from './code.api'
import type { Code } from './code.api'

/** 플랫 코드 목록 → p_code_id 기준 계층 트리(최상위=ROOT). 자식 있으면 children 부여 */
function buildTree(list: Code[]): Code[] {
  const byParent = new Map<string, Code[]>()
  for (const c of list) {
    const p = c.pCodeId ?? 'ROOT'
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p)!.push(c)
  }
  const build = (pid: string): Code[] =>
    (byParent.get(pid) ?? []).map((c) => {
      const children = build(c.dbKey!)
      return children.length ? { ...c, children } : { ...c }
    })
  return build('ROOT')
}

/** 검색어로 트리 가지치기 — 노드 자신 또는 하위가 매치되면 유지 */
function filterTree(nodes: Code[], kw: string): Code[] {
  const res: Code[] = []
  for (const n of nodes) {
    const kids = n.children ? filterTree(n.children, kw) : []
    const hit = (n.codeNm ?? '').includes(kw) || (n.dbKey ?? '').includes(kw)
    if (hit || kids.length) res.push(kids.length ? { ...n, children: kids } : { ...n, children: undefined })
  }
  return res
}

function allKeys(nodes: Code[]): string[] {
  return nodes.flatMap((n) => [n.dbKey!, ...(n.children ? allKeys(n.children) : [])])
}

/**
 * 공통코드 관리 — 계층 트리 + 분할 마스터-디테일.
 * 좌: 트리 목록(ROOT 하위 그룹→펼치면 하위코드, 같은 부모 내 ▲▼ 순서변경), 우: 상세·등록·수정 폼.
 */
export default function CodeListPage() {
  const [flat, setFlat] = useState<Code[]>([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [expandedRowKeys, setExpandedRowKeys] = useState<string[]>([])

  const reload = () => {
    setLoading(true)
    codeApi
      .tree()
      .then(setFlat)
      .catch((e) => message.error(e instanceof Error ? e.message : '목록 조회 실패'))
      .finally(() => setLoading(false))
  }
  useEffect(reload, [])

  const treeData = useMemo(() => {
    const full = buildTree(flat)
    return keyword ? filterTree(full, keyword) : full
  }, [flat, keyword])

  // 검색 시 매치 결과 전체 펼침
  useEffect(() => {
    if (keyword) setExpandedRowKeys(allKeys(treeData))
  }, [keyword, treeData])

  const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm<Code>(codeApi, reload)
  const isEdit = mode === 'edit'

  const move = (row: Code, dir: 'UP' | 'DOWN') =>
    runWithMessage(() => codeApi.moveOrdr(row.dbKey!, dir), '순서를 변경했습니다.', reload)

  /** 선택 코드 하위에 신규: 부모코드 + 다음 코드ID(연번) + 다음 정렬순서 자동 채움 */
  const addChild = async () => {
    const parent = selectedKey
    if (!parent) return
    openNew()
    try {
      const next = await codeApi.nextChild(parent)
      form.setFieldsValue({ pCodeId: parent, dbKey: next.dbKey, ordr: next.ordr })
    } catch {
      form.setFieldsValue({ pCodeId: parent })
    }
  }

  const columns: TableColumnsType<Code> = [
    { title: '코드ID', dataIndex: 'dbKey', width: 180 },
    { title: '코드명', dataIndex: 'codeNm' },
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
    <Card title="코드 트리">
      <SearchBar
        fields={[{ type: 'text', name: 'searchKeyword', placeholder: '코드ID/명', width: 260 }]}
        onSearch={(v) => setKeyword(v.searchKeyword ?? '')}
      />
      <Table<Code>
        rowKey="dbKey"
        size="small"
        columns={columns}
        dataSource={treeData}
        loading={loading}
        pagination={false}
        expandable={{ expandedRowKeys, onExpandedRowsChange: (keys) => setExpandedRowKeys(keys as string[]) }}
        rowClassName={(r) => (r.dbKey === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r.dbKey!), style: { cursor: 'pointer' } })}
      />
    </Card>
  )

  const detail = (
    <Card
      title="상세 / 등록 / 수정"
      extra={
        <Space>
          <Button onClick={addChild} disabled={!selectedKey}>하위코드추가</Button>
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
        <Form form={form} layout="vertical" initialValues={{ pCodeId: 'ROOT' }}>
          <Form.Item name="dbKey" label="코드ID" rules={[{ required: true, message: '코드ID를 입력하세요.' }]}>
            <Input disabled={isEdit} />
          </Form.Item>
          <Form.Item name="pCodeId" label="상위코드" rules={[{ required: true, message: '상위코드를 입력하세요.' }]}>
            <Input disabled={isEdit} />
          </Form.Item>
          <Form.Item name="codeNm" label="코드명" rules={[{ required: true, message: '코드명을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="codeDesc" label="설명">
            <Input />
          </Form.Item>
          <Form.Item name="ordr" label="정렬순서(자동)">
            <Input disabled />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="공통코드 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
