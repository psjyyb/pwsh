import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Empty, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import { hobbyApi } from '../hobby/hobby.api'
import { RECRUIT_LIST_URL, recruitApi, applyApi } from '../../gen/recruit/recruit.api'
import type { Recruit, RecruitApply } from '../../gen/recruit/recruit.api'

const statusTag = (cd?: string, nm?: string) =>
  cd === 'RECRUIT02' ? <Tag>마감</Tag> : <Tag color="green">{nm || '모집중'}</Tag>
const applyTag = (cd?: string, nm?: string) =>
  cd === 'APPLY02' ? <Tag color="green">수락</Tag>
    : cd === 'APPLY03' ? <Tag color="red">거절</Tag>
      : <Tag color="orange">{nm || '대기'}</Tag>
const cap = (r: Recruit) => `${r.acceptedCnt ?? 0}${Number(r.capacity) > 0 ? ` / ${r.capacity}` : ' (제한없음)'}`

/**
 * 모집 관리(관리자) — 전체 모집 목록/검색 + 상세(신청자 목록) + 삭제(숨김)/강제 마감 + 신청자 수락·거절·강제 제거.
 * 백엔드는 모집 도메인의 소유자 인가가 assertOwnerOrAdmin이라 관리자가 모든 모집·신청에 조치 가능(추가 API 불필요).
 */
export default function RecruitMngPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<Recruit>(RECRUIT_LIST_URL, { hobbyId: '', statusCd: '' })
  const [sel, setSel] = useState<Recruit | null>(null)
  const [applies, setApplies] = useState<RecruitApply[]>([])
  const [applyLoading, setApplyLoading] = useState(false)
  const [hobbies, setHobbies] = useState<{ value: string; label: string }[]>([])

  // 취미별 검색용 드롭다운. 취미는 관리자가 추가하므로 목록을 서버에서 받아 채운다.
  useEffect(() => {
    hobbyApi
      .listAll()
      .then((list) => setHobbies(list.map((h) => ({ value: h.rowId!, label: h.hobbyName ?? '' }))))
      .catch(() => setHobbies([]))
  }, [])

  const loadApplies = async (recruitId: string) => {
    setApplyLoading(true)
    try { setApplies(await applyApi.listByRecruit(recruitId)) }
    catch { setApplies([]) }
    finally { setApplyLoading(false) }
  }

  const openRow = async (rowId: string) => {
    try {
      setSel(await recruitApi.view(rowId)) // viewUp=false: 관리자 조회는 조회수 증가 안 함
      loadApplies(rowId)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회 실패')
    }
  }

  // 조치 후 상세/신청자/목록 동기화
  const refresh = async () => {
    if (!sel?.rowId) return
    try { setSel(await recruitApi.view(sel.rowId)) } catch { /* 삭제됨 등 */ }
    if (sel.rowId) loadApplies(sel.rowId)
    reload()
  }

  const changeStatus = async (statusCd: string) => {
    if (!sel?.rowId) return
    try { await recruitApi.changeStatus(sel.rowId, statusCd); message.success(statusCd === 'RECRUIT02' ? '마감했습니다.' : '다시 모집중으로 변경했습니다.'); refresh() }
    catch (e) { message.error(e instanceof Error ? e.message : '처리 실패') }
  }

  const removeRecruit = async () => {
    if (!sel?.rowId) return
    try { await recruitApi.remove(sel.rowId); message.success('모집을 삭제(숨김)했습니다.'); setSel(null); setApplies([]); reload() }
    catch (e) { message.error(e instanceof Error ? e.message : '삭제 실패') }
  }

  const changeApply = async (rowId: string, applyCd: string) => {
    try { await applyApi.changeStatus(rowId, applyCd); message.success(applyCd === 'APPLY02' ? '수락했습니다.' : '거절했습니다.'); refresh() }
    catch (e) { message.error(e instanceof Error ? e.message : '처리 실패') }
  }

  const removeApply = async (rowId: string) => {
    try { await applyApi.cancel(rowId); message.success('신청자를 제거했습니다.'); refresh() }
    catch (e) { message.error(e instanceof Error ? e.message : '제거 실패') }
  }

  const columns: TableColumnsType<Recruit> = [
    { title: '취미', width: 90, render: (_, r) => r.hobbyName ?? '-' },
    { title: '제목', ellipsis: true, render: (_, r) => r.title },
    { title: '주최자', width: 110, render: (_, r) => r.regName || r.regId },
    { title: '인원', width: 110, align: 'center', render: (_, r) => cap(r) },
    { title: '상태', width: 80, align: 'center', render: (_, r) => statusTag(r.statusCd, r.statusName) },
  ]

  const applyColumns: TableColumnsType<RecruitApply> = [
    { title: '신청자', render: (_, r) => r.nickname || r.memberId },
    { title: '상태', width: 70, align: 'center', render: (_, r) => applyTag(r.applyCd, r.applyName) },
    { title: '메모', ellipsis: true, render: (_, r) => r.applyMemo || '-' },
    { title: '신청일', width: 100, render: (_, r) => r.regDt },
    {
      title: '처리', width: 170, align: 'center',
      render: (_, r) => (
        <Space size={4}>
          {r.applyCd !== 'APPLY02' && <Button size="small" type="link" onClick={() => changeApply(r.rowId!, 'APPLY02')}>수락</Button>}
          {r.applyCd !== 'APPLY03' && <Button size="small" type="link" onClick={() => changeApply(r.rowId!, 'APPLY03')}>거절</Button>}
          <Popconfirm title="이 신청을 제거하시겠습니까?" onConfirm={() => removeApply(r.rowId!)} okText="제거" okButtonProps={{ danger: true }} cancelText="취소">
            <Button size="small" type="link" danger>제거</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const list = (
    <Card title="모집 목록">
      <Space style={{ marginBottom: 8 }} wrap>
        <Select
          defaultValue="" style={{ width: 150 }} showSearch optionFilterProp="label"
          onChange={(v) => search({ hobbyId: v })}
          options={[{ value: '', label: '전체 취미' }, ...hobbies]}
        />
        <Select
          defaultValue="" style={{ width: 130 }} onChange={(v) => search({ statusCd: v })}
          options={[{ value: '', label: '전체 상태' }, { value: 'RECRUIT01', label: '모집중' }, { value: 'RECRUIT02', label: '마감' }]}
        />
        <SearchBar fields={[{ type: 'text', name: 'filterKeyword', placeholder: '제목·내용', width: 200 }]} onSearch={(v) => search(v)} />
      </Space>
      <Table<Recruit>
        rowKey="rowId" size="small" scroll={{ x: 'max-content' }} columns={columns} dataSource={rows} loading={loading}
        rowClassName={(r) => (r.rowId === sel?.rowId ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r.rowId!), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const detail = (
    <Card
      title="상세 / 신청자 관리"
      extra={sel && (
        <Space>
          {sel.statusCd === 'RECRUIT02'
            ? <Button size="small" onClick={() => changeStatus('RECRUIT01')}>재개</Button>
            : <Button size="small" onClick={() => changeStatus('RECRUIT02')}>강제 마감</Button>}
          <Popconfirm title="모집을 삭제(숨김) 처리하시겠습니까?" onConfirm={removeRecruit} okText="삭제" okButtonProps={{ danger: true }} cancelText="취소">
            <Button size="small" danger>삭제/숨김</Button>
          </Popconfirm>
        </Space>
      )}
    >
      {!sel ? (
        <Empty description="모집을 선택하세요." />
      ) : (
        <>
          <Descriptions size="small" column={1} bordered labelStyle={{ width: 90 }}>
            <Descriptions.Item label="제목">{sel.title}</Descriptions.Item>
            <Descriptions.Item label="취미">{sel.hobbyName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="주최자">{sel.regName || sel.regId}</Descriptions.Item>
            <Descriptions.Item label="지역">{[sel.areaName, sel.region].filter(Boolean).join(' ') || '-'}</Descriptions.Item>
            <Descriptions.Item label="일정">{sel.meetDt || '-'}</Descriptions.Item>
            <Descriptions.Item label="인원">{cap(sel)}</Descriptions.Item>
            <Descriptions.Item label="상태">{statusTag(sel.statusCd, sel.statusName)}</Descriptions.Item>
            <Descriptions.Item label="내용"><div style={{ whiteSpace: 'pre-wrap' }}>{sel.content || '-'}</div></Descriptions.Item>
          </Descriptions>
          <div style={{ margin: '14px 0 6px', fontWeight: 600 }}>신청자 ({applies.length})</div>
          {applies.length === 0 && !applyLoading
            ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="신청자가 없습니다." />
            : <Table<RecruitApply> rowKey="rowId" size="small" scroll={{ x: 'max-content' }} columns={applyColumns} dataSource={applies} loading={applyLoading} pagination={false} />}
        </>
      )}
    </Card>
  )

  return (
    <Card title="모집 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
