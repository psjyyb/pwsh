import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Descriptions, Empty, Form, Grid, Input, InputNumber, Modal, Pagination, Popconfirm,
  Select, Space, Table, Tag, message,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import DateField from '../../common/adm/components/DateField'
import CodeSelect from '../../common/adm/components/CodeSelect'
import { getClaims, isAdmin, tokenStore } from '../../auth/token'
import { hobbyApi } from '../../adm/hobby/hobby.api'
import { recruitApi, applyApi } from './recruit.api'
import RecruitChatPanel from './RecruitChatPanel'
import type { Recruit, RecruitApply } from './recruit.api'
import { hasViewedRecently, markViewed } from '../../common/util/postView'
import MemberAvatar from '../../common/gen/components/MemberAvatar'
import ReportAction from '../../common/gen/components/ReportAction'
import PlaceMap from '../../common/gen/components/PlaceMap'
import PlacePicker from '../../common/gen/components/PlacePicker'
import { bookmarkApi } from '../../api/bookmark'
import { downloadIcs, safeFileName } from '../../common/util/ics'

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
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('list')
  const screens = Grid.useBreakpoint()
  const [categories, setCategories] = useState<Category[]>([])

  const [rows, setRows] = useState<Recruit[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [filterCat, setFilterCat] = useState<string | undefined>()
  const [filterArea, setFilterArea] = useState<string | undefined>() // 시/도 필터(AREA00)
  const [filterStatus, setFilterStatus] = useState<string | undefined>()
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const [recruit, setRecruit] = useState<Recruit | null>(null)
  const [bookmarked, setBookmarked] = useState(false) // 이 모집의 내 북마크 여부
  const [applies, setApplies] = useState<RecruitApply[]>([]) // 주최자용 신청자 목록
  const [myApply, setMyApply] = useState<RecruitApply | null>(null) // 현재 모집에 대한 내 신청
  const [applyMemo, setApplyMemo] = useState('')

  const [form] = Form.useForm()
  const [editKey, setEditKey] = useState<string | null>(null)
  const [copyForm] = Form.useForm() // 다음 회차 만들기(정기 모임)
  const [copyOpen, setCopyOpen] = useState(false)
  const [copying, setCopying] = useState(false)

  const pageSize = 10
  const meId = getClaims()?.sub
  const loggedIn = !!tokenStore.get()
  const admin = isAdmin()
  const catName = (id?: string) => categories.find((c) => c.hobbyId === id)?.name

  // 취미 카테고리 = hobby 카탈로그
  useEffect(() => {
    hobbyApi.listAll()
      .then((list) => setCategories(list.map((h) => ({ hobbyId: h.rowId!, name: h.hobbyName ?? '' }))))
      .catch(() => {})
  }, [])

  const loadList = useCallback(
    async (p = 1, kw = keyword) => {
      setLoading(true)
      try {
        const res = await recruitApi.list({
          hobbyId: filterCat, statusCd: filterStatus, areaCd: filterArea, filterKeyword: kw, pageNo: p, pageSize,
        })
        setRows(res.list)
        setTotal(res.totalCount)
        setPageNo(p)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '목록 조회 실패')
      } finally {
        setLoading(false)
      }
    },
    [filterCat, filterStatus, filterArea, keyword],
  )

  useEffect(() => {
    if (mode === 'list') loadList(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterCat, filterStatus, filterArea])

  const openView = async (rowId: string) => {
    try {
      const countUp = !hasViewedRecently(`recruit-${rowId}`) // 새로고침 중복증가 방지
      const r = await recruitApi.view(rowId, countUp)
      if (countUp) markViewed(`recruit-${rowId}`)
      setRecruit(r)
      if (meId) {
        bookmarkApi.ids('RECRUIT').then((ids) => setBookmarked(ids.includes(rowId))).catch(() => setBookmarked(false))
      } else {
        setBookmarked(false)
      }
      const owner = admin || r.mineYn === 'Y'
      setApplies(owner ? await applyApi.listByRecruit(rowId) : [])
      if (loggedIn && !owner) {
        const mine = await applyApi.mine()
        setMyApply(mine.find((a) => a.recruitId === rowId) ?? null)
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
      setEditKey(r.rowId!)
      form.setFieldsValue({
        hobbyId: r.hobbyId, title: r.title, capacity: r.capacity ? Number(r.capacity) : undefined,
        areaCd: r.areaCd, region: r.region, meetDt: r.meetDt, content: r.content,
        // 장소는 4개 컬럼을 한 덩어리로 다룬다(좌표만 남거나 이름만 남는 상태를 만들지 않는다)
        place: r.placeName ? { placeName: r.placeName, addr: r.addr, lat: r.lat, lng: r.lng } : undefined,
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
      areaCd: v.areaCd ?? '', region: v.region ?? '', meetDt: v.meetDt ?? '', content: v.content ?? '',
      // 장소를 지우면 빈 문자열로 보내 서버가 NULL로 되돌린다
      placeName: v.place?.placeName ?? '', addr: v.place?.addr ?? '',
      lat: v.place?.lat ?? '', lng: v.place?.lng ?? '',
    }
    try {
      if (editKey) await recruitApi.update({ ...payload, rowId: editKey })
      else await recruitApi.insert(payload)
      message.success('저장되었습니다.')
      setMode('list')
      loadList(editKey ? pageNo : 1)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장 실패')
    }
  }

  const remove = async () => {
    if (!recruit) return
    try {
      await recruitApi.remove(recruit.rowId!)
      message.success('삭제되었습니다.')
      setMode('list')
      loadList(1)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '삭제 실패')
    }
  }

  /** 참석 결과 기록(주최자) — 빈 값이면 미기록으로 되돌림. */
  const setAttend = async (applyId: string, attendCd: string) => {
    if (!recruit) return
    try {
      await applyApi.setAttend(applyId, attendCd)
      setApplies(await applyApi.listByRecruit(recruit.rowId!))
      message.success(attendCd ? '참석 결과를 기록했습니다.' : '참석 기록을 지웠습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '기록 실패')
    }
  }

  /** 이 모임을 개인 캘린더로 내보내기(.ics). 종일 일정으로 만든다(모임일에 시간이 없음). */
  const addToCalendar = () => {
    if (!recruit?.meetDt) return
    downloadIcs([{
      uid: `recruit-${recruit.rowId}@pwsh`,
      title: recruit.title ?? '모임',
      date: recruit.meetDt,
      // 캘린더 앱이 길찾기에 쓰는 값이라 정확한 주소가 있으면 그걸 우선한다
      location: recruit.addr
        ? [recruit.placeName, recruit.addr].filter(Boolean).join(' ')
        : [recruit.areaName, recruit.region].filter(Boolean).join(' '),
      description: recruit.content ?? '',
      url: `${window.location.origin}/gen/recruit/${recruit.rowId}`,
    }], `${safeFileName(recruit.title ?? 'meeting')}.ics`)
    message.success('캘린더 파일을 내려받았습니다. 열면 캘린더에 추가됩니다.')
  }

  /** 북마크 토글 — 결과 상태를 서버 응답(markedYn)으로 반영. */
  const toggleBookmark = async () => {
    if (!recruit) return
    try {
      const r = await bookmarkApi.toggle('RECRUIT', recruit.rowId!)
      const on = r.markedYn === 'Y'
      setBookmarked(on)
      message.success(on ? '북마크에 저장했습니다.' : '북마크를 해제했습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '북마크 처리 실패')
    }
  }

  /** 다음 회차 만들기 — 일정만 새로 받고 나머지는 원본 값을 채워 보여준다(그대로 두면 복제). */
  const openCopy = () => {
    if (!recruit) return
    copyForm.setFieldsValue({
      title: recruit.title,
      capacity: recruit.capacity ? Number(recruit.capacity) : undefined,
      areaCd: recruit.areaCd, region: recruit.region, meetDt: undefined,
      place: recruit.placeName
        ? { placeName: recruit.placeName, addr: recruit.addr, lat: recruit.lat, lng: recruit.lng }
        : undefined,
    })
    setCopyOpen(true)
  }

  const doCopy = async () => {
    if (!recruit) return
    const v = await copyForm.validateFields()
    setCopying(true)
    try {
      const newId = await recruitApi.copy(recruit.rowId!, {
        title: v.title, capacity: v.capacity != null ? String(v.capacity) : '',
        areaCd: v.areaCd ?? '', region: v.region ?? '', meetDt: v.meetDt,
        placeName: v.place?.placeName ?? '', addr: v.place?.addr ?? '',
        lat: v.place?.lat ?? '', lng: v.place?.lng ?? '',
      })
      setCopyOpen(false)
      message.success('다음 회차 모집을 만들었습니다. 이전 회차 참여자에게 알림을 보냈습니다.')
      navigate(`/gen/recruit/${newId}`) // 라우트 변경 → 새 모집 상세로 전환
    } catch (e) {
      message.error(e instanceof Error ? e.message : '다음 회차 생성 실패')
    } finally {
      setCopying(false)
    }
  }

  const changeStatus = async (statusCd: string) => {
    if (!recruit) return
    try {
      await recruitApi.changeStatus(recruit.rowId!, statusCd)
      message.success(statusCd === STATUS_CLOSED ? '마감되었습니다.' : '다시 모집합니다.')
      openView(recruit.rowId!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '상태 변경 실패')
    }
  }

  const doApply = async () => {
    if (!recruit) return
    try {
      await applyApi.apply(recruit.rowId!, applyMemo.trim() || undefined)
      message.success('참여 신청했습니다.')
      openView(recruit.rowId!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '신청 실패')
    }
  }

  const cancelApply = async () => {
    if (!myApply) return
    try {
      await applyApi.cancel(myApply.rowId!)
      message.success('신청을 취소했습니다.')
      if (recruit) openView(recruit.rowId!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '취소 실패')
    }
  }

  // 거절된 신청 → 기존 신청 취소 후 재신청(중복신청 차단 우회, 직관적 재도전)
  const reApply = async () => {
    if (!myApply || !recruit) return
    try {
      await applyApi.cancel(myApply.rowId!)
      await applyApi.apply(recruit.rowId!, applyMemo.trim() || undefined)
      message.success('다시 신청했습니다.')
      openView(recruit.rowId!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '재신청 실패')
    }
  }

  const decideApply = async (rowId: string, applyCd: string) => {
    try {
      await applyApi.changeStatus(rowId, applyCd)
      message.success(applyCd === 'APPLY02' ? '수락했습니다.' : '거절했습니다.')
      if (recruit) setApplies(await applyApi.listByRecruit(recruit.rowId!))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '처리 실패')
    }
  }

  // ===== 목록 =====
  if (mode === 'list') {
    const columns: TableColumnsType<Recruit> = [
      { title: '취미', width: 110, render: (_, r) => r.hobbyName ?? catName(r.hobbyId) ?? '-' },
      { title: '모임명', render: (_, r) => r.title },
      // 지도로 고른 장소가 있으면 그게 가장 구체적인 정보다
      { title: '지역', width: 160, render: (_, r) => r.placeName || [r.areaName, r.region].filter(Boolean).join(' ') || '-' },
      { title: '일정', dataIndex: 'meetDt', width: 120, render: (v) => v || '-' },
      {
        title: '인원', width: 90, align: 'center',
        render: (_, r) => `${r.acceptedCnt ?? 0}${Number(r.capacity) > 0 ? ` / ${r.capacity}` : ''}`,
      },
      { title: '상태', width: 90, align: 'center', render: (_, r) => statusTag(r.statusCd, r.statusName) },
      { title: '주최자', width: 150, render: (_, r) => <MemberAvatar fileId={r.regProfileFileId} name={r.regName || '-'} handle={r.regHandle} size={24} /> },
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
          {/* 지역은 표준 코드(시/도)로 필터 — 자유입력 편차 없이 정확히 걸러진다 */}
          <CodeSelect
            pCodeId="AREA00" allowClear placeholder="지역 전체" style={{ width: 130 }}
            value={filterArea} onChange={setFilterArea}
          />
          <Input.Search
            placeholder="모임명 검색" allowClear style={{ width: 220 }}
            onSearch={(v) => { setKeyword(v); loadList(1, v) }}
          />
        </Space>
        {rows.length === 0 && !loading ? (
          <Empty description="등록된 모집이 없습니다." />
        ) : !screens.md ? (
          <>
            <Space direction="vertical" style={{ width: '100%' }} size={10}>
              {rows.map((r) => (
                <Card key={r.rowId} size="small" hoverable onClick={() => openView(r.rowId!)} styles={{ body: { padding: 12 } }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' }}>
                    <span style={{ fontWeight: 600 }}>{r.title}</span>
                    {statusTag(r.statusCd, r.statusName)}
                  </div>
                  <div style={{ fontSize: 12, color: '#888', marginTop: 6, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                    <span>{r.hobbyName ?? catName(r.hobbyId) ?? '-'}</span>
                    <span>{r.placeName || [r.areaName, r.region].filter(Boolean).join(' ') || '-'}</span>
                    <span>{r.meetDt || '-'}</span>
                    <span>인원 {r.acceptedCnt ?? 0}{Number(r.capacity) > 0 ? ` / ${r.capacity}` : ''}</span>
                  </div>
                  <div style={{ marginTop: 6 }}><MemberAvatar fileId={r.regProfileFileId} name={r.regName || '-'} handle={r.regHandle} size={20} /></div>
                </Card>
              ))}
            </Space>
            <div style={{ textAlign: 'center', marginTop: 12 }}>
              <Pagination current={pageNo} pageSize={pageSize} total={total} onChange={(p) => loadList(p)} showSizeChanger={false} simple />
            </div>
          </>
        ) : (
          <Table<Recruit>
            rowKey="rowId" size="small" loading={loading} columns={columns} dataSource={rows}
            onRow={(r) => ({ onClick: () => openView(r.rowId!), style: { cursor: 'pointer' } })}
            pagination={{
              current: pageNo, pageSize: pageSize, total, onChange: (p) => loadList(p),
              showSizeChanger: false,
            }}
          />
        )}
      </Card>
    )
  }

  // ===== 상세 =====
  if (mode === 'view' && recruit) {
    const owner = admin || recruit.mineYn === 'Y'
    // 모임 종료 여부(마감 또는 모임일 경과) — 참석 기록 가능 시점. 서버와 동일 기준.
    const finished = recruit.statusCd === STATUS_CLOSED
      || (!!recruit.meetDt && recruit.meetDt < new Date().toISOString().slice(0, 10))
    const open = recruit.statusCd === STATUS_OPEN
    const pastMeet = !!recruit.meetDt && recruit.meetDt < new Date().toISOString().slice(0, 10)
    // 정원 충족으로 닫힌 상태(서버 판정과 동일 기준) — 이 경우에만 대기 신청을 받는다
    const capacityFull = Number(recruit.capacity) > 0
      && Number(recruit.acceptedCnt ?? 0) >= Number(recruit.capacity)
    const waitlistOpen = !open && capacityFull && !pastMeet
    // 정원에 여유가 생겼는데 아직 마감 상태 — 주최자에게 재개를 안내(자동 재개는 하지 않는다)
    const hasRoomButClosed = !open && !capacityFull && !pastMeet && Number(recruit.capacity) > 0
    // 단체 대화 자격: 주최자 본인(mineYn) 또는 수락된 참여자. admin은 owner지만 멤버는 아니다(사적 대화).
    const chatMember = recruit.mineYn === 'Y' || myApply?.applyCd === 'APPLY02'
    return (
      <Card
        title={recruit.title}
        extra={
          <Space>
            {owner && (
              <>
                {/* 정기 모임: 같은 조건으로 일정만 바꿔 새 모집을 연다(참여자·대화는 복제되지 않음) */}
                <Button onClick={openCopy}>다음 회차</Button>
                <Button onClick={() => changeStatus(open ? STATUS_CLOSED : STATUS_OPEN)}>
                  {open ? '모집 마감' : '다시 모집'}
                </Button>
                <Button onClick={() => openWrite(recruit)}>수정</Button>
                <Popconfirm title="삭제하시겠습니까?" onConfirm={remove} okText="삭제" cancelText="취소">
                  <Button danger>삭제</Button>
                </Popconfirm>
              </>
            )}
            {/* 신청 전에 궁금한 걸 물어볼 수 있게 — 주최자와의 쪽지 대화로 이동(모집 정보는 자동 입력) */}
            {loggedIn && !owner && recruit.regHandle && (
              <Button onClick={() => navigate(`/gen/message?with=${recruit.regHandle}&ref=recruit:${recruit.rowId}`)}>
                문의하기
              </Button>
            )}
            {meId && (
              <Button type={bookmarked ? 'primary' : 'default'} ghost={bookmarked} onClick={toggleBookmark}>
                {bookmarked ? '🔖 북마크됨' : '🔖 북마크'}
              </Button>
            )}
            {/* 일정이 있는 모집만 — 개인 캘린더(구글·애플·아웃룩)로 가져가 잊지 않게 */}
            {recruit.meetDt && (
              <Button onClick={addToCalendar}>📅 캘린더에 추가</Button>
            )}
            <Button onClick={() => { setMode('list'); loadList(pageNo) }}>목록</Button>
          </Space>
        }
      >
        <Descriptions bordered column={2} size="small">
          <Descriptions.Item label="취미">{recruit.hobbyName ?? catName(recruit.hobbyId) ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="상태">{statusTag(recruit.statusCd, recruit.statusName)}</Descriptions.Item>
          <Descriptions.Item label="지역">{[recruit.areaName, recruit.region].filter(Boolean).join(' ') || '-'}</Descriptions.Item>
          <Descriptions.Item label="일정">{recruit.meetDt || '-'}</Descriptions.Item>
          <Descriptions.Item label="모집 인원">{Number(recruit.capacity) > 0 ? `${recruit.capacity}명` : '제한 없음 (0명)'}</Descriptions.Item>
          <Descriptions.Item label="신청 현황">
            <Space size={6} wrap>
              <span>수락 {recruit.acceptedCnt ?? 0} · 신청 {recruit.applyCnt ?? 0}</span>
              {capacityFull && <Tag color="red">정원 마감</Tag>}
              {waitlistOpen && <Tag color="orange">대기 접수중</Tag>}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="주최자"><MemberAvatar fileId={recruit.regProfileFileId} name={recruit.regName || '-'} handle={recruit.regHandle} /></Descriptions.Item>
          <Descriptions.Item label="등록일">{recruit.regDt}</Descriptions.Item>
          {/* 만날 장소 — 좌표가 있으면 마커 지도까지. 장소 미정 모집은 이 칸을 아예 그리지 않는다 */}
          {(recruit.placeName || recruit.addr) && (
            <Descriptions.Item label="만날 장소" span={2}>
              <PlaceMap placeName={recruit.placeName} addr={recruit.addr} lat={recruit.lat} lng={recruit.lng} />
            </Descriptions.Item>
          )}
        </Descriptions>

        <div style={{ whiteSpace: 'pre-wrap', marginTop: 16, minHeight: 60 }}>{recruit.content}</div>
        {loggedIn && !owner && (
          <div style={{ marginTop: 8, textAlign: 'right' }}><ReportAction targetType="RECRUIT" targetId={recruit.rowId!} /></div>
        )}

        {/* 참여 신청 영역(비주최자) */}
        {!owner && (
          <div style={{ marginTop: 20, borderTop: '1px solid #eee', paddingTop: 12 }}>
            {!loggedIn ? (
              <span style={{ color: '#888' }}>로그인 후 참여 신청할 수 있습니다.</span>
            ) : myApply ? (
              <Space wrap>
                <span>내 신청 상태: {applyTag(myApply.applyCd, myApply.applyName)}</span>
                {myApply.applyCd === 'APPLY01' && myApply.waitNo && capacityFull && (
                  <Tag color="orange">대기 {myApply.waitNo}번</Tag>
                )}
                {myApply.applyCd === 'APPLY03' && open && (
                  <Button size="small" type="primary" onClick={reApply}>다시 신청</Button>
                )}
                {myApply.applyCd !== 'APPLY02' && (
                  <Popconfirm title="신청을 취소하시겠습니까?" onConfirm={cancelApply} okText="취소" cancelText="닫기">
                    <Button size="small">신청 취소</Button>
                  </Popconfirm>
                )}
              </Space>
            ) : open || waitlistOpen ? (
              <div>
                {waitlistOpen && (
                  <div style={{ marginBottom: 8, color: '#d46b08' }}>
                    정원이 찼습니다. 대기로 신청하면 자리가 날 때 주최자가 수락할 수 있습니다.
                  </div>
                )}
                <Space.Compact style={{ width: '100%' }}>
                  <Input
                    placeholder="주최자에게 한마디 (선택)" value={applyMemo}
                    onChange={(e) => setApplyMemo(e.target.value)} maxLength={500}
                  />
                  <Button type="primary" onClick={doApply}>{waitlistOpen ? '대기 신청' : '참여 신청'}</Button>
                </Space.Compact>
              </div>
            ) : (
              <span style={{ color: '#888' }}>
                {pastMeet ? '이미 지난 모임입니다.' : '마감된 모집입니다.'}
              </span>
            )}
          </div>
        )}

        {/* 모임 대화 — 주최자 본인 또는 수락(APPLY02)된 참여자만. 관리자라도 멤버가 아니면 서버가 막는다. */}
        {chatMember && <RecruitChatPanel recruitId={recruit.rowId!} />}

        {/* 신청자 목록(주최자·관리자) */}
        {owner && (
          <div style={{ marginTop: 20, borderTop: '1px solid #eee', paddingTop: 12 }}>
            <b>신청자 {applies.length}명</b>
            {hasRoomButClosed && (
              <div style={{ marginTop: 6, color: '#d46b08' }}>
                정원에 여유가 있습니다(수락 {recruit.acceptedCnt ?? 0}/{recruit.capacity}). 대기자를 수락하거나,
                새 신청을 다시 받으려면 위의 <b>다시 모집</b>을 눌러 주세요.
              </div>
            )}
            <Table<RecruitApply>
              rowKey="rowId" size="small" style={{ marginTop: 8 }} pagination={false}
              dataSource={applies}
              locale={{ emptyText: '신청자가 없습니다.' }}
              columns={[
                { title: '닉네임', render: (_, a) => <MemberAvatar name={a.nickname || '-'} handle={a.memberHandle} size={22} /> },
                {
                  title: '상태', width: 110,
                  render: (_, a) => (
                    <Space size={4} wrap>
                      {applyTag(a.applyCd, a.applyName)}
                      {a.applyCd === 'APPLY01' && a.waitNo && capacityFull && (
                        <Tag color="orange">대기 {a.waitNo}</Tag>
                      )}
                    </Space>
                  ),
                },
                { title: '메모', dataIndex: 'applyMemo', render: (v) => v || '-' },
                { title: '신청일', dataIndex: 'regDt', width: 140 },
                {
                  // 참석 기록: 모임이 끝난 뒤(마감·일정 경과) 수락된 참여자에게만. 서버도 동일 조건을 강제한다.
                  title: '참석', width: 190,
                  render: (_, a) => {
                    if (a.applyCd !== 'APPLY02') return <span style={{ color: '#ccc' }}>-</span>
                    if (!finished) return <span style={{ fontSize: 12, color: '#bbb' }}>모임 후 기록</span>
                    return (
                      <Select
                        size="small" style={{ width: 170 }} value={a.attendCd ?? ''}
                        onChange={(v) => setAttend(a.rowId!, v)}
                        options={[
                          { value: '', label: '미기록' },
                          { value: 'ATTEND01', label: '참석' },
                          { value: 'ATTEND02', label: '불참(통보)' },
                          { value: 'ATTEND03', label: '노쇼' },
                        ]}
                      />
                    )
                  },
                },
                {
                  title: '처리', width: 140,
                  render: (_, a) =>
                    a.applyCd === 'APPLY01' ? (
                      <Space>
                        <Button size="small" type="primary" onClick={() => decideApply(a.rowId!, 'APPLY02')}>수락</Button>
                        <Button size="small" danger onClick={() => decideApply(a.rowId!, 'APPLY03')}>거절</Button>
                      </Space>
                    ) : '-',
                },
              ]}
            />
          </div>
        )}

        {/* 다음 회차 만들기 — 일정만 필수, 나머지는 원본 값이 채워져 있고 고쳐도 된다 */}
        <Modal
          open={copyOpen} title="다음 회차 만들기" okText="만들기" cancelText="취소"
          onOk={doCopy} onCancel={() => setCopyOpen(false)} confirmLoading={copying} destroyOnHidden
        >
          <div style={{ color: '#888', fontSize: 13, marginBottom: 12 }}>
            같은 조건으로 새 모집을 엽니다. 이전 회차의 신청자·대화는 넘어오지 않고,
            <b> 지난 회차 참여자에게 알림</b>이 갑니다.
          </div>
          <Form form={copyForm} layout="vertical">
            <Form.Item name="title" label="모임명" rules={[{ required: true, message: '모임명을 입력하세요.' }]}>
              <Input maxLength={200} />
            </Form.Item>
            <Form.Item
              name="meetDt" label="다음 모임 일정"
              rules={[
                { required: true, message: '일정을 선택하세요.' },
                {
                  validator: (_, v: string) =>
                    !v || v >= new Date().toISOString().slice(0, 10)
                      ? Promise.resolve()
                      : Promise.reject(new Error('지난 날짜는 선택할 수 없습니다.')),
                },
              ]}
            >
              <DateField />
            </Form.Item>
            <Space size={12} wrap align="start">
              <Form.Item name="capacity" label="모집 인원">
                <InputNumber min={0} max={999} addonAfter="명" style={{ width: 130 }} />
              </Form.Item>
              <Form.Item name="areaCd" label="지역(시/도)" style={{ minWidth: 150 }}>
                <CodeSelect pCodeId="AREA00" placeholder="시/도 선택" allowClear />
              </Form.Item>
              <Form.Item name="region" label="상세 지역">
                <Input style={{ width: 180 }} maxLength={100} />
              </Form.Item>
            </Space>
            {/* 같은 곳에서 다시 모이는 경우가 많아 원본 장소를 채워 둔다(바꿀 수도 있게) */}
            <Form.Item name="place" label="만날 장소">
              <PlacePicker />
            </Form.Item>
          </Form>
        </Modal>
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
          <Form.Item name="areaCd" label="지역(시/도)" style={{ minWidth: 150 }}>
            <CodeSelect pCodeId="AREA00" placeholder="시/도 선택" allowClear />
          </Form.Item>
          <Form.Item name="region" label="상세 지역">
            <Input style={{ width: 220 }} maxLength={100} placeholder="예: 서울/경기" />
          </Form.Item>
          <Form.Item name="meetDt" label="모임 일정">
            <DateField allowClear />
          </Form.Item>
        </Space>
        {/* 지도에서 고른 장소(이름·주소·좌표)를 한 값으로 담는다 — 처음 오는 사람이 헤매지 않게 */}
        <Form.Item name="place" label="만날 장소">
          <PlacePicker />
        </Form.Item>
        <Form.Item name="content" label="모집 설명">
          <Input.TextArea autoSize={{ minRows: 4, maxRows: 12 }} placeholder="모임 소개, 준비물, 진행 방식 등" />
        </Form.Item>
      </Form>
    </Card>
  )
}
