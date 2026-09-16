import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Empty, Input, Popconfirm, Progress, Select, Space, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SplitLayout from '../../common/adm/components/SplitLayout'
import CodeSelect from '../../common/adm/components/CodeSelect'
import { runWithMessage } from '../../common/util/action'
import { formApi } from '../form/form.api'
import type { Form, FormField } from '../form/form.api'
import { FORM_ANSWER_LIST_URL, formAnswerApi } from './formanswer.api'
import type { FormAnswer, FormStat } from './formanswer.api'

const STATUS_COLOR: Record<string, string> = {
  ANSWER01: 'blue', ANSWER02: 'gold', ANSWER03: 'green', ANSWER04: 'red',
}

/** CSV 한 칸 이스케이프(쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싼다) */
function csvCell(v: string): string {
  const s = v ?? ''
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

/**
 * 폼 응답 관리 — 폼을 고르고 응답을 본다. 목록 | 상세(처리상태·메모) + 문항별 집계 + CSV 내려받기.
 *
 * <p>개인정보 문항이 있는 폼이면 상세·내려받기가 <b>개인정보 접근로그에 남는다</b>(서버가 자동 기록).
 */
export default function FormAnswerListPage() {
  const [forms, setForms] = useState<Form[]>([])
  const [formId, setFormId] = useState<string>('')
  const [fields, setFields] = useState<FormField[]>([])
  const [selected, setSelected] = useState<FormAnswer | null>(null)
  const [stats, setStats] = useState<FormStat[] | null>(null)
  const [memo, setMemo] = useState('')

  const { rows, total, loading, page, pageSize, reload, search, changePage } =
    useList<FormAnswer>(FORM_ANSWER_LIST_URL)

  useEffect(() => {
    formApi.combo().then((list) => {
      setForms(list)
      if (list.length > 0) setFormId(list[0].rowId!)
    }).catch(() => {})
  }, [])

  // 폼을 바꾸면 문항 정의를 다시 읽는다(상세에서 문항 제목을 붙여 보여주기 위함)
  useEffect(() => {
    if (!formId) return
    setSelected(null)
    setStats(null)
    formApi.view(formId).then((f) => setFields(f.fields ?? [])).catch(() => {})
    search({ formId })
  }, [formId]) // eslint-disable-line react-hooks/exhaustive-deps

  const openRow = async (r: FormAnswer) => {
    setStats(null)
    const detail = await formAnswerApi.view(r.rowId!)
    setSelected(detail)
    setMemo(detail.adminMemo ?? '')
  }

  const changeStatus = (statusCd: string) =>
    runWithMessage(
      () => formAnswerApi.changeStatus(selected!.rowId!, statusCd, memo),
      '처리상태를 변경했습니다.',
      () => { reload(); openRow(selected!) },
    )

  const removeAnswer = () =>
    runWithMessage(() => formAnswerApi.remove(selected!.rowId!), '삭제했습니다.', () => {
      setSelected(null)
      reload()
    })

  const showStats = async () => {
    setSelected(null)
    setStats(await formAnswerApi.stats(formId))
  }

  const downloadCsv = async () => {
    const data = await formAnswerApi.exportAll(formId)
    if (data.list.length === 0) {
      message.warning('내려받을 응답이 없습니다.')
      return
    }
    const cols = data.fields
    const header = ['응답ID', '제출자', '처리상태', '제출일시', ...cols.map((c) => c.label ?? '')]
    const lines = [header.map(csvCell).join(',')]
    for (const a of data.list) {
      lines.push([
        a.rowId ?? '', a.memberId ?? '(비로그인)', a.statusName ?? '', a.regDt ?? '',
        ...cols.map((c) => (a.values?.[c.rowId!] ?? '').replace(/\n/g, ' / ')),
      ].map(csvCell).join(','))
    }
    // BOM을 붙여야 엑셀이 UTF-8로 열어 한글이 깨지지 않는다
    const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${forms.find((f) => f.rowId === formId)?.title ?? 'form'}_응답.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  const columns: TableColumnsType<FormAnswer> = [
    {
      title: '상태',
      width: 80,
      render: (_, r) => <Tag color={STATUS_COLOR[r.statusCd ?? ''] ?? 'default'}>{r.statusName}</Tag>,
    },
    { title: '제출자', width: 130, render: (_, r) => r.memberId ?? <span style={{ color: '#999' }}>비로그인</span> },
    { title: '제출일시', dataIndex: 'regDt', width: 170 },
    { title: 'IP', dataIndex: 'regIp', width: 120 },
  ]

  const list = (
    <Card title="응답 목록">
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          style={{ width: 260 }}
          value={formId || undefined}
          placeholder="폼 선택"
          options={forms.map((f) => ({ value: f.rowId!, label: f.title! }))}
          onChange={setFormId}
        />
        <CodeSelect
          allowAll
          pCodeId="ANSWER00"
          style={{ width: 140 }}
          onChange={(v) => search({ formId, statusCd: (v as string) ?? '' })}
        />
        <Button onClick={showStats} disabled={!formId}>문항별 집계</Button>
        <Button onClick={downloadCsv} disabled={!formId}>CSV 내려받기</Button>
      </Space>
      <Table<FormAnswer>
        rowKey="rowId"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selected?.rowId ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const statsView = (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      {stats!.map((s) => (
        <div key={s.fieldId}>
          <div style={{ fontWeight: 600, marginBottom: 6 }}>
            {s.label} <span style={{ color: '#999', fontWeight: 400 }}>({s.answerCnt}건)</span>
          </div>
          {s.options ? (
            s.options.map((o) => (
              <div key={o.label} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{ width: 140, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {o.label}
                </span>
                <Progress
                  percent={s.answerCnt ? Math.round((o.cnt / s.answerCnt) * 100) : 0}
                  size="small"
                  style={{ flex: 1, marginBottom: 0 }}
                />
                <span style={{ width: 40, textAlign: 'right' }}>{o.cnt}</span>
              </div>
            ))
          ) : (
            <div style={{ color: '#999' }}>자유 입력 문항 — 개별 응답에서 확인하세요.</div>
          )}
        </div>
      ))}
      {stats!.length === 0 && <Empty description="집계할 문항이 없습니다(개인정보 문항은 집계하지 않습니다)." />}
    </Space>
  )

  const detail = (
    <Card
      title={stats ? '문항별 집계' : '응답 상세'}
      extra={
        selected && (
          <Space>
            <CodeSelect pCodeId="ANSWER00" value={selected.statusCd} style={{ width: 130 }} onChange={(v) => changeStatus(v as string)} />
            <Popconfirm title="삭제하시겠습니까?" onConfirm={removeAnswer} okText="삭제" cancelText="취소">
              <Button danger>삭제</Button>
            </Popconfirm>
          </Space>
        )
      }
    >
      {stats ? (
        statsView
      ) : !selected ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>행을 선택하세요.</div>
      ) : (
        <>
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="제출자">{selected.memberId ?? '비로그인'}</Descriptions.Item>
            <Descriptions.Item label="제출일시">{selected.regDt}</Descriptions.Item>
            {fields.map((f) => (
              <Descriptions.Item key={f.rowId} label={f.label}>
                <span style={{ whiteSpace: 'pre-line' }}>{selected.values?.[f.rowId!] || '-'}</span>
                {f.privacyYn === 'Y' && <Tag color="orange" style={{ marginLeft: 6 }}>개인정보</Tag>}
              </Descriptions.Item>
            ))}
          </Descriptions>
          <div style={{ marginTop: 12 }}>
            <div style={{ marginBottom: 6, fontWeight: 600 }}>담당자 메모(응답자에게 보이지 않습니다)</div>
            <Input.TextArea rows={3} value={memo} onChange={(e) => setMemo(e.target.value)} />
            <Button style={{ marginTop: 8 }} onClick={() => changeStatus(selected.statusCd!)}>
              메모 저장
            </Button>
          </div>
        </>
      )}
    </Card>
  )

  return (
    <Card title="폼 응답 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
