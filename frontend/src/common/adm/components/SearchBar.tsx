import { useState } from 'react'
import type { ReactNode } from 'react'
import { Button, Input, Select, Space } from 'antd'
import CodeSelect from './CodeSelect'

/**
 * 관리자 목록 공통 검색 컴포넌트 (다중 조건). 원하는 검색 조건을 fields로 선언만 하면 됨.
 *   <SearchBar
 *     fields={[
 *       { type: 'text', name: 'filterKeyword', placeholder: '아이디/이름' },
 *       { type: 'code', name: 'typeCd', pCodeId: 'MEM00', placeholder: '회원유형' },
 *       { type: 'select', name: 'useYn', options: [{value:'Y',label:'사용'},{value:'N',label:'미사용'}] },
 *     ]}
 *     onSearch={(values) => search(values)}
 *     onCreate={openNew}
 *   />
 * onSearch에는 모든 필드 값(빈값 포함)이 객체로 전달됨 → useList.search에 그대로 넘기면
 * 백엔드가 빈값 조건은 무시(<if test="x != null and x != ''">).
 */
export type SearchField =
  | { type: 'text'; name: string; placeholder?: string; width?: number }
  | { type: 'select'; name: string; placeholder?: string; options: { value: string; label: string }[]; width?: number }
  | { type: 'code'; name: string; placeholder?: string; pCodeId: string; width?: number }
  /**
   * "검색 대상 선택 + 검색어" 조합. 드롭다운으로 검색할 컬럼을 바꿔가며 하나의 검색어로 조회.
   * conditions.value = 백엔드 filterField 분기값(예: 'access_contents','reg_id').
   */
  | {
      type: 'keyword'
      name?: string // 검색어 파라미터명(기본 filterKeyword)
      condName?: string // 검색조건 파라미터명(기본 filterField)
      conditions: { value: string; label: string }[]
      placeholder?: string
      width?: number
    }

interface Props {
  fields: SearchField[]
  onSearch: (values: Record<string, string>) => void
  onCreate?: () => void
  createText?: string
  /** 검색바 좌측에 함께 배치할 요소(예: 영역 탭 Segmented) */
  leftExtra?: ReactNode
}

export default function SearchBar({ fields, onSearch, onCreate, createText = '등록', leftExtra }: Props) {
  // keyword 필드는 검색조건 드롭다운의 기본값(첫 조건)으로 초기화
  const buildDefaults = () => {
    const d: Record<string, string> = {}
    fields.forEach((f) => {
      if (f.type === 'keyword') d[f.condName ?? 'filterField'] = f.conditions[0]?.value ?? ''
    })
    return d
  }
  const [values, setValues] = useState<Record<string, string>>(buildDefaults)
  const setVal = (name: string, v: string) => setValues((prev) => ({ ...prev, [name]: v }))

  const doSearch = () => onSearch(values)
  const reset = () => {
    const cleared = buildDefaults()
    fields.forEach((f) => {
      if (f.type === 'keyword') cleared[f.name ?? 'filterKeyword'] = ''
      else cleared[f.name] = ''
    })
    setValues(cleared)
    onSearch(cleared)
  }

  const renderField = (f: SearchField) => {
    if (f.type === 'keyword') {
      const kwName = f.name ?? 'filterKeyword'
      const condName = f.condName ?? 'filterField'
      return (
        <Space.Compact key={kwName}>
          <Select
            size="large"
            value={values[condName] ?? f.conditions[0]?.value ?? ''}
            options={f.conditions}
            style={{ width: 130 }}
            onChange={(v) => setVal(condName, v)}
          />
          <Input
            size="large"
            allowClear
            placeholder={f.placeholder ?? '검색어'}
            value={values[kwName] ?? ''}
            style={{ width: f.width ?? 220 }}
            onChange={(e) => setVal(kwName, e.target.value)}
            onPressEnter={doSearch}
          />
        </Space.Compact>
      )
    }
    const val = values[f.name] ?? ''
    if (f.type === 'text') {
      return (
        <Input
          key={f.name}
          size="large"
          allowClear
          placeholder={f.placeholder}
          value={val}
          style={{ width: f.width ?? 220 }}
          onChange={(e) => setVal(f.name, e.target.value)}
          onPressEnter={doSearch}
        />
      )
    }
    if (f.type === 'select') {
      // 맨 앞 "전체"(빈값=조건 미사용)로 초기 상태 복귀 가능
      return (
        <Select
          key={f.name}
          size="large"
          options={[{ value: '', label: '전체' }, ...f.options]}
          value={val}
          style={{ width: f.width ?? 160 }}
          onChange={(v) => setVal(f.name, v ?? '')}
        />
      )
    }
    return (
      <CodeSelect
        key={f.name}
        size="large"
        allowAll
        pCodeId={f.pCodeId}
        value={val}
        style={{ width: f.width ?? 160 }}
        onChange={(v) => setVal(f.name, (v as string) ?? '')}
      />
    )
  }

  // 선택(드롭다운) 조건을 앞에, 검색어 입력(text/keyword)을 검색 버튼 쪽(뒤)에 배치
  const isSelectType = (f: SearchField) => f.type === 'select' || f.type === 'code'
  const orderedFields = [...fields.filter(isSelectType), ...fields.filter((f) => !isSelectType(f))]

  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 12,
        alignItems: 'center',
        padding: 16,
        marginBottom: 16,
        background: '#fafafa',
        border: '1px solid #f0f0f0',
        borderRadius: 8,
      }}
    >
      {leftExtra}
      {orderedFields.map(renderField)}
      <Button type="primary" size="large" onClick={doSearch}>
        검색
      </Button>
      <Button size="large" onClick={reset}>
        초기화
      </Button>
      {onCreate && (
        <Space style={{ marginLeft: 'auto' }}>
          <Button type="primary" size="large" onClick={onCreate}>
            {createText}
          </Button>
        </Space>
      )}
    </div>
  )
}
