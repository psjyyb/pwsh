import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Descriptions, Empty, Form, Grid, Input, InputNumber, Pagination, Popconfirm,
  Select, Space, Table, Tag, message,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useParams, useSearchParams } from 'react-router-dom'
import DateField from '../../common/adm/components/DateField'
import { getClaims, isAdmin, tokenStore } from '../../auth/token'
import { hobbyApi } from '../../adm/hobby/hobby.api'
import { recruitApi, applyApi } from './recruit.api'
import type { Recruit, RecruitApply } from './recruit.api'
import { hasViewedRecently, markViewed } from '../../common/util/bbsView'
import UserAvatar from '../../common/gen/components/UserAvatar'
import ReportAction from '../../common/gen/components/ReportAction'

type Mode = 'list' | 'view' | 'write'
interface Category { hobbyId: string; name: string }

const STATUS_OPEN = 'RECRUIT01' // 모집중
const STATUS_CLOSED = 'RECRUIT02' // 마감
const statusTag = (cd?: string, nm?: string) =>
  cd === STATUS_OPEN ? <Tag color="green">{nm ?? '모집중'}</Tag> : <Tag>{nm ?? '마감'}</Tag>
const applyTag = (cd?: string, nm?: string) => {
  const color = cd === 'APPLY02' ? 'blue' : cd === 'APPLY03' ? 'red' : 'default'
  return <Tag color={color}>{nm ?? '대기'}</Tag>
}

/**
 * 모집(취미 함께할 사람 구하기) — 목록/상세/작성을 한 화면에서 모드 전환.
 * 취미(카테고리)는 GEN 메뉴 "취미게시판" 하위(=취미 게시판)에서 도출.
 * 조회는 공개, 등록/신청/수락은 로그인 필요(백엔드가 강제).
 */
export default function RecruitPage() {
  const { id: routeId } = useParams()
  const [searchParams] = useSearchParams()
  const [mode, setMode] = useState<Mode>('list')
  const screens = Grid.useBreakpoint()
  const [categories, setCategories] = useState<Category[]>([])

  const [rows, setRows] = useState<Recruit[]>([])
  const [total, setTotal] = useState(0)
  const [pageIndex, setPageIndex] = useState(1)
  const [filterCat, setFilterCat] = useState<string | undefined>()
  const [filterStatus, setFilterStatus] = useState<string | undefined>()
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const [recruit, setRecruit] = useState<Recruit | null>(null)
  const [applies, setApplies] = useState<RecruitApply[]>([]) // 주최자용 신청자 목록
  const [myApply, setMyApply] = useState<RecruitApply | null>(null) // 현재 모집에 대한 내 신청
  const [applyMemo, setApplyMemo] = useState('')

  const [form] = Form.useForm()
  const [editKey, setEditKey] = useState<string | null>(null)

  const size = 10
  const meId = getClaims()?.sub
  const loggedIn = !!tokenStore.get()
  const admin = isAdmin()
  const catName = (id?: string) => categories.find((c) => c.hobbyId === id)?.name

  // 취미 카테고리 = t_hobby 카탈로그
  useEffect(() => {
    hobbyApi.listAll()
      .then((list) => setCategories(list.map((h) => ({ hobbyId: h.dbKey!, name: h.hobbyNm ?? '' }))))
      .catch(() => {})
  }, [])

  const loadList = useCallback(
    async (p = 1) => {
      setLoading(true)
      try {
        const res = await recruitApi.list({
          hobbyId: filterCat, statusCd: filterStatus, searchKeyword: keyword, pageIndex: p, size,
        })
        setRows(res.list)
        setTotal(res.totCnt)
        setPageIndex(p)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '목록 조회 실패')
      } finally {
        setLoading(false)
      }
    },
    [filterCat, filterStatus, keyword],
  )

  useEffect(() => {
    if (mode === 'list') loadList(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterCat, filterStatus])

  const openView = async (dbKey: string) => {
    try {
      const countUp = !hasViewedRecently(`recruit-${dbKey}`) // 새로고침 중복증가 방지
      const r = await recruitApi.view(dbKey, countUp)
      if (countUp) markViewed(`recruit-${dbKey}`)
      setRecruit(r)
      const owner = admin || (!!meId && r.regId === meId)
      setApplies(owner ? await applyApi.listByRecruit(dbKey) : [])
      if (loggedIn && !owner) {
        const mine = await applyApi.mine()
        setMyApply(mine.find((a) => a.recruitId === dbKey) ?? null)
      } else {
        setMyApply(null)
      }
      setApplyMemo('')
      setMode('view')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회 실패')
    }
  }

  // 외부 진입: /gen/recruit/:id → 해당 모집 상세, ?hobby=id → 취미 필터
  useEffect(() => {
    const hobbyParam = searchParams.get('hobby')
    if (hobbyParam) setFilterCat(hobbyParam)
    if (routeId) openView(routeId)
    else setMode('list')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeId, searchParams])

  const openWrite = (r?: Recruit) => {
    if (r) {
      setEditKey(r.dbKey!)
      form.setFieldsValue({
        hobbyId: r.hobbyId, title: r.title, capacity: r.capacity ? Number(r.capacity) : undefined,
        region: r.region, meetDt: r.meetDt, content: r.content,
      })
    } else {
      setEditKey(null)
      form.resetFields()
    }
    setMode('write')
  }

  const save = async () => {
    const v = await form.validateFields()
    const payload: Partial<Recruit> = {
      hobbyId: v.hobbyId, title: v.title,
      capacity: v.capacity != null ? String(v.capacity) : '',
      region: v.region ?? '', meetDt: v.meetDt ?? '', content: v.content ?? '',
    }
    try {
      if (editKey) await recruitApi.update({ ...payload, dbKey: editKey })
      else await recruitApi.insert(payload)
      message.success('저장되었습니다.')
      setMode('list')
      loadList(editKey ? pageIndex : 1)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장 실패')
    }
  }

  const remove = async () => {
    if (!recruit) return
    try {
      await recruitApi.remove(recruit.dbKey!)
      message.success('삭제되었습니다.')
      setMode('list')
      loadList(1)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '삭제 실패')
    }
  }

  const changeStatus = async (statusCd: string) => {
    if (!recruit) return
    try {
      await recruitApi.changeStatus(recruit.dbKey!, statusCd)
      message.success(statusCd === STATUS_CLOSED ? '마감되었습니다.' : '다시 모집합니다.')
      openView(recruit.dbKey!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '상태 변경 실패')
    }
  }

  const doApply = async () => {
    if (!recruit) return
    try {
      await applyApi.apply(recruit.dbKey!, applyMemo.trim() || undefined)
      message.success('참여 신청했습니다.')
      openView(recruit.dbKey!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '신청 실패')
    }
  }

  const cancelApply = async () => {
    if (!myApply) return
    try {
      await applyApi.cancel(myApply.dbKey!)
      message.success('신청을 취소했습니다.')
      if (recruit) openView(recruit.dbKey!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '취소 실패')
    }
  }

  // 거절된 신청 → 기존 신청 취소 후 재신청(중복신청 차단 우회, 직관적 재도전)
  const reApply = async () => {
    if (!myApply || !recruit) return
    try {
      await applyApi.cancel(myApply.dbKey!)
      await applyApi.apply(recruit.dbKey!, applyMemo.trim() || undefined)
      message.success('다시 신청했습니다.')
      openView(recruit.dbKey!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '재신청 실패')
    }
  }

  const decideApply = async (dbKey: string, applyStatus: string) => {
    try {
      await applyApi.changeStatus(dbKey, applyStatus)
      message.success(applyStatus === 'APPLY02' ? '수락했습니다.' : '거절했습니다.')
      if (recruit) setApplies(await applyApi.listByRecruit(recruit.dbKey!))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '처리 실패')
    }
  }

  // ===== 목록 =====
  if (mode === 'list') {
    const columns: TableColumnsType<Recruit> = [
      { title: '취미', width: 110, render: (_, r) => r.hobbyNm ?? catName(r.hobbyId) ?? '-' },
      { title: '모임명', render: (_, r) => r.title },
      { title: '지역', dataIndex: 'region', width: 120, render: (v) => v || '-' },
      { title: '일정', dataIndex: 'meetDt', width: 120, render: (v) => v || '-' },
      {
        title: '인원', width: 90, align: 'center',
        render: (_, r) => `${r.acceptedCnt ?? 0}${Number(r.capacity) > 0 ? ` / ${r.capacity}` : ''}`,
      },
      { title: '상태', width: 90, align: 'center', render: (_, r) => statusTag(r.statusCd, r.statusNm) },
      { title: '주최자', width: 150, render: (_, r) => <UserAvatar fileId={r.regProfileFileId} name={r.regNm || r.regId} size={24} /> },
    ]
    return (
      <Card
        title="모집"
        extra={loggedIn ? <Button type="primary" onClick={() => openWrite()}>모집 등록</Button> : null}
      >
        <Space wrap style={{ marginBottom: 12 }}>
          <Select
            allowClear placeholder="취미 전체" style={{ width: 140 }} value={filterCat}
            onChange={setFilterCat}
            options={categories.map((c) => ({ value: c.hobbyId, label: c.name }))}
          />
          <Select
            allowClear placeholder="상태 전체" style={{ width: 120 }} value={filterStatus}
            onChange={setFilterStatus}
            options={[{ value: STATUS_OPEN, label: '모집중' }, { value: STATUS_CLOSED, label: '마감' }]}
          />
          <Input.Search
            placeholder="모임명 검색" allowClear style={{ width: 220 }}
            onSearch={(v) => { setKeyword(v); loadList(1) }}
          />
        </Space>
        {rows.length === 0 && !loading ? (
          <Empty description="등록된 모집이 없습니다." />
        ) : !screens.md ? (
          <>
            <Space direction="vertical" style={{ width: '100%' }} size={10}>
              {rows.map((r) => (
                <Card key={r.dbKey} size="small" hoverable onClick={() => openView(r.dbKey!)} styles={{ body: { padding: 12 } }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' }}>
                    <span style={{ fontWeight: 600 }}>{r.title}</span>
                    {statusTag(r.statusCd, r.statusNm)}
                  </div>
                  <div style={{ fontSize: 12, color: '#888', marginTop: 6, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                    <span>{r.hobbyNm ?? catName(r.hobbyId) ?? '-'}</span>
                    <span>{r.region || '-'}</span>
                    <span>{r.meetDt || '-'}</span>
                    <span>인원 {r.acceptedCnt ?? 0}{Number(r.capacity) > 0 ? ` / ${r.capacity}` : ''}</span>
                  </div>
                  <div style={{ marginTop: 6 }}><UserAvatar fileId={r.regProfileFileId} name={r.regNm || r.regId} size={20} /></div>
                </Card>
              ))}
            </Space>
            <div style={{ textAlign: 'center', marginTop: 12 }}>
              <Pagination current={pageIndex} pageSize={size} total={total} onChange={(p) => loadList(p)} showSizeChanger={false} simple />
            </div>
          </>
        ) : (
          <Table<Recruit>
            rowKey="dbKey" size="small" loading={loading} columns={columns} dataSource={rows}
            onRow={(r) => ({ onClick: () => openView(r.dbKey!), style: { cursor: 'pointer' } })}
            pagination={{
              current: pageIndex, pageSize: size, total, onChange: (p) => loadList(p),
              showSizeChanger: false,
            }}
          />
        )}
      </Card>
    )
  }

  // ===== 상세 =====
  if (mode === 'view' && recruit) {
    const owner = admin || (!!meId && recruit.regId === meId)
    const open = recruit.statusCd === STATUS_OPEN
    return (
      <Card
        title={recruit.title}
        extra={
          <Space>
            {owner && (
              <>
                <Button onClick={() => changeStatus(open ? STATUS_CLOSED : STATUS_OPEN)}>
                  {open ? '모집 마감' : '다시 모집'}
                </Button>
                <Button onClick={() => openWrite(recruit)}>수정</Button>
                <Popconfirm title="삭제하시겠습니까?" onConfirm={remove} okText="삭제" cancelText="취소">
                  <Button danger>삭제</Button>
                </Popconfirm>
              </>
            )}
            <Button onClick={() => { setMode('list'); loadList(pageIndex) }}>목록</Button>
          </Space>
        }
      >
        <Descriptions bordered column={2} size="small">
          <Descriptions.Item label="취미">{recruit.hobbyNm ?? catName(recruit.hobbyId) ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="상태">{statusTag(recruit.statusCd, recruit.statusNm)}</Descriptions.Item>
          <Descriptions.Item label="지역">{recruit.region || '-'}</Descriptions.Item>
          <Descriptions.Item label="일정">{recruit.meetDt || '-'}</Descriptions.Item>
          <Descriptions.Item label="모집 인원">{Number(recruit.capacity) > 0 ? `${recruit.capacity}명` : '제한 없음 (0명)'}</Descriptions.Item>
          <Descriptions.Item label="신청 현황">
            수락 {recruit.acceptedCnt ?? 0} · 신청 {recruit.applyCnt ?? 0}
          </Descriptions.Item>
          <Descriptions.Item label="주최자"><UserAvatar fileId={recruit.regProfileFileId} name={recruit.regNm || recruit.regId} /></Descriptions.Item>
          <Descriptions.Item label="등록일">{recruit.regDt}</Descriptions.Item>
        </Descriptions>

        <div style={{ whiteSpace: 'pre-wrap', marginTop: 16, minHeight: 60 }}>{recruit.content}</div>
        {loggedIn && !owner && (
          <div style={{ marginTop: 8, textAlign: 'right' }}><ReportAction targetType="RECRUIT" targetId={recruit.dbKey!} /></div>
        )}

        {/* 참여 신청 영역(비주최자) */}
        {!owner && (
          <div style={{ marginTop: 20, borderTop: '1px solid #eee', paddingTop: 12 }}>
            {!loggedIn ? (
              <span style={{ color: '#888' }}>로그인 후 참여 신청할 수 있습니다.</span>
            ) : myApply ? (
              <Space wrap>
                <span>내 신청 상태: {applyTag(myApply.applyStatus, myApply.applyStatusNm)}</span>
                {myApply.applyStatus === 'APPLY03' && open && (
                  <Button size="small" type="primary" onClick={reApply}>다시 신청</Button>
                )}
                {myApply.applyStatus !== 'APPLY02' && (
                  <Popconfirm title="신청을 취소하시겠습니까?" onConfirm={cancelApply} okText="취소" cancelText="닫기">
                    <Button size="small">신청 취소</Button>
                  </Popconfirm>
                )}
              </Space>
            ) : open ? (
              <Space.Compact style={{ width: '100%' }}>
                <Input
                  placeholder="주최자에게 한마디 (선택)" value={applyMemo}
                  onChange={(e) => setApplyMemo(e.target.value)} maxLength={500}
                />
                <Button type="primary" onClick={doApply}>참여 신청</Button>
              </Space.Compact>
            ) : (
              <span style={{ color: '#888' }}>마감된 모집입니다.</span>
            )}
          </div>
        )}

        {/* 신청자 목록(주최자·관리자) */}
        {owner && (
          <div style={{ marginTop: 20, borderTop: '1px solid #eee', paddingTop: 12 }}>
            <b>신청자 {applies.length}명</b>
            <Table<RecruitApply>
              rowKey="dbKey" size="small" style={{ marginTop: 8 }} pagination={false}
              dataSource={applies}
              locale={{ emptyText: '신청자가 없습니다.' }}
              columns={[
                { title: '닉네임', render: (_, a) => a.nickname || a.userId },
                { title: '상태', width: 80, render: (_, a) => applyTag(a.applyStatus, a.applyStatusNm) },
                { title: '메모', dataIndex: 'applyMemo', render: (v) => v || '-' },
                { title: '신청일', dataIndex: 'regDt', width: 140 },
                {
                  title: '처리', width: 140,
                  render: (_, a) =>
                    a.applyStatus === 'APPLY01' ? (
                      <Space>
                        <Button size="small" type="primary" onClick={() => decideApply(a.dbKey!, 'APPLY02')}>수락</Button>
                        <Button size="small" danger onClick={() => decideApply(a.dbKey!, 'APPLY03')}>거절</Button>
                      </Space>
                    ) : '-',
                },
              ]}
            />
          </div>
        )}
      </Card>
    )
  }

  // ===== 작성/수정 =====
  return (
    <Card
      title={editKey ? '모집 수정' : '모집 등록'}
      extra={
        <Space>
          <Button onClick={() => setMode('list')}>목록</Button>
          <Button type="primary" onClick={save}>저장</Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" style={{ maxWidth: 640 }}>
        <Form.Item name="hobbyId" label="취미" rules={[{ required: true, message: '취미를 선택하세요.' }]}>
          <Select
            placeholder="취미 선택"
            options={categories.map((c) => ({ value: c.hobbyId, label: c.name }))}
          />
        </Form.Item>
        <Form.Item name="title" label="모임명" rules={[{ required: true, message: '모임명을 입력하세요.' }]}>
          <Input maxLength={200} placeholder="예: 주말 북한산 정기 산행" />
        </Form.Item>
        <Space size={16} wrap align="start">
          <Form.Item name="capacity" label="모집 인원">
            <InputNumber min={0} max={999} addonAfter="명" style={{ width: 140 }} placeholder="0=제한없음" />
          </Form.Item>
          <Form.Item name="region" label="활동 지역">
            <Input style={{ width: 220 }} maxLength={100} placeholder="예: 서울/경기" />
          </Form.Item>
          <Form.Item name="meetDt" label="모임 일정">
            <DateField allowClear />
          </Form.Item>
        </Space>
        <Form.Item name="content" label="모집 설명">
          <Input.TextArea autoSize={{ minRows: 4, maxRows: 12 }} placeholder="모임 소개, 준비물, 진행 방식 등" />
        </Form.Item>
      </Form>
    </Card>
  )
}
