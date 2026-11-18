import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Col, Row, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import { SEARCH_STAT_LIST_URL, searchStatApi } from './searchstat.api'
import type { SearchStat, SearchStatDaily } from './searchstat.api'

const DAY_OPTIONS = [
  { value: '7', label: '최근 7일' },
  { value: '30', label: '최근 30일' },
  { value: '90', label: '최근 90일' },
]
/** 검색바의 '전체'(빈 값) = 서버가 받는 가장 긴 기간 */
const ALL_DAYS = '365'

/**
 * 검색 통계(CMS 1.2.38) — 통합검색의 날짜·검색어별 횟수(누가 검색했는지는 남기지 않는다).
 *
 * <p>결과 없던 검색어는 콘텐츠가 비는 곳·오타를 찾는 단서다. 인기 검색어에 내보이면 안 되는 말(사람 이름·연락처 등)은
 * 여기서 숨긴다 — 금칙어가 든 말은 따로 숨기지 않아도 인기 검색어에 나오지 않는다.
 */
export default function SearchStatListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<SearchStat>(SEARCH_STAT_LIST_URL, {
    days: '7',
  })
  const [days, setDays] = useState('7')
  const [daily, setDaily] = useState<SearchStatDaily[]>([])

  const loadDaily = useCallback(async (d: string) => {
    try {
      setDaily((await searchStatApi.daily(d)).list)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '일별 합계를 불러오지 못했습니다.')
    }
  }, [])

  useEffect(() => {
    loadDaily('7')
  }, [loadDaily])

  const toggleHide = async (r: SearchStat) => {
    const next = r.hiddenYn === 'Y' ? 'N' : 'Y'
    try {
      await searchStatApi.hide(r.keyword!, next)
      message.success(next === 'Y' ? '인기 검색어에서 숨겼습니다.' : '숨김을 풀었습니다.')
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '바꾸지 못했습니다.')
    }
  }

  const columns: TableColumnsType<SearchStat> = [
    { title: '순위', width: 60, render: (_, __, i) => (page - 1) * pageSize + i + 1 },
    { title: '검색어', dataIndex: 'keyword' },
    { title: '검색 수', dataIndex: 'searchCnt', width: 90, align: 'right' },
    { title: '결과 없음', dataIndex: 'zeroCnt', width: 90, align: 'right' },
    { title: '마지막 검색', dataIndex: 'lastDate', width: 110 },
    {
      title: '인기 검색어',
      width: 200,
      render: (_, r) => (
        <span>
          {r.banYn === 'Y' ? (
            <Tag color="red">금칙어 포함(제외됨)</Tag>
          ) : r.hiddenYn === 'Y' ? (
            <Tag>숨김</Tag>
          ) : null}
          {r.banYn !== 'Y' && (
            <Button size="small" type="link" onClick={() => toggleHide(r)}>
              {r.hiddenYn === 'Y' ? '숨김 해제' : '숨기기'}
            </Button>
          )}
        </span>
      ),
    },
  ]

  const dailyColumns: TableColumnsType<SearchStatDaily> = [
    { title: '날짜', dataIndex: 'statDate' },
    { title: '검색 수', dataIndex: 'searchCnt', align: 'right' },
    { title: '결과 없음', dataIndex: 'zeroCnt', align: 'right' },
    { title: '검색어 수', dataIndex: 'keywordCnt', align: 'right' },
  ]

  return (
    <Card title="검색 통계">
      <SearchBar
        fields={[
          { type: 'select', name: 'days', options: DAY_OPTIONS, defaultValue: '7', width: 130 },
          { type: 'select', name: 'zeroOnly', placeholder: '전체 검색어', options: [{ value: 'Y', label: '결과 없던 검색어만' }], width: 170 },
          { type: 'text', name: 'filterKeyword', placeholder: '검색어', width: 200 },
        ]}
        onSearch={(v) => {
          const d = v.days || ALL_DAYS
          setDays(d)
          search({ ...v, days: d })
          loadDaily(d)
        }}
      />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={15}>
          <Table<SearchStat>
            rowKey="keyword"
            size="small"
            columns={columns}
            dataSource={rows}
            loading={loading}
            pagination={{ current: page, pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
          />
        </Col>
        <Col xs={24} lg={9}>
          <Card size="small" title={`일별 합계 (${DAY_OPTIONS.find((o) => o.value === days)?.label ?? '최근 1년'})`}
            styles={{ body: { padding: 0 } }}>
            <Table<SearchStatDaily>
              rowKey="statDate"
              size="small"
              columns={dailyColumns}
              dataSource={daily}
              pagination={daily.length > 10 ? { pageSize: 10, size: 'small' } : false}
            />
          </Card>
        </Col>
      </Row>
    </Card>
  )
}
