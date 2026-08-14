import { useEffect, useRef, useState } from 'react'
import { Button, Card, Empty, Form, Input, Modal, Rate, Segmented, Space, Switch, Table, Tag, Tooltip, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useNavigate } from 'react-router-dom'
import { tokenStore } from '../../auth/token'
import { me, changePw, updateProfileImage, withdraw } from '../../api/auth'
import { fileApi } from '../../api/file'
import { applyApi } from '../recruit/recruit.api'
import type { Recruit, RecruitApply } from '../recruit/recruit.api'
import type { Bbs } from '../../adm/bbs/bbs.api'
import { myPosts, myRecruits, changeNickname } from './mypage.api'
import ScheduleCalendar from './ScheduleCalendar'
import type { ScheduleItem } from './ScheduleCalendar'
import { reviewApi } from '../../api/review'
import type { ReviewStats, ReviewTarget } from '../../api/review'
import { getUserProfile } from '../../api/profile'
import type { AttendStats } from '../../api/profile'
import { badgesOf, num } from '../badges'
import { bookmarkApi } from '../../api/bookmark'
import type { Bookmark } from '../../api/bookmark'
import { notificationApi } from '../../api/notification'
import type { NotiSetting } from '../../api/notification'
import { blockApi } from '../../api/block'
import type { Block } from '../../api/block'
import { gen } from '../theme'

/**
 * 마이페이지 — 내 정보(닉네임/비번변경)·내 글·내 모집·내 신청. (담은 취미는 '나의 취미' 탭)
 * 로그인 필요(비로그인은 로그인 유도).
 */
export default function MyPage() {
  const navigate = useNavigate()
  const loggedIn = !!tokenStore.get()

  const [userId, setUserId] = useState('')
  const [nickname, setNickname] = useState('')
  const [nickInput, setNickInput] = useState('')
  const [nickOpen, setNickOpen] = useState(false)
  const [pwOpen, setPwOpen] = useState(false)
  const [pwForm] = Form.useForm()
  const [profileFileId, setProfileFileId] = useState<string | undefined>()
  const [uploading, setUploading] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const [withdrawOpen, setWithdrawOpen] = useState(false)
  const [withdrawPw, setWithdrawPw] = useState('')

  const [posts, setPosts] = useState<Bbs[]>([])
  const [recruits, setRecruits] = useState<Recruit[]>([])
  const [applies, setApplies] = useState<RecruitApply[]>([])
  // 후기 쓰기(종료된 모임에서 함께한 회원)
  const [reviewTargets, setReviewTargets] = useState<ReviewTarget[]>([])
  const [reviewOpen, setReviewOpen] = useState(false)
  const [reviewTarget, setReviewTarget] = useState<ReviewTarget | null>(null)
  const [rating, setRating] = useState(5)
  const [reviewText, setReviewText] = useState('')
  const [reviewSaving, setReviewSaving] = useState(false)

  // 북마크(스크랩) / 알림 수신 설정
  const [bmPosts, setBmPosts] = useState<Bookmark[]>([])
  const [bmRecruits, setBmRecruits] = useState<Bookmark[]>([])
  const [notiSet, setNotiSet] = useState<NotiSetting>({})
  const [notiSaving, setNotiSaving] = useState(false)

  const [blocks, setBlocks] = useState<Block[]>([])
  const [scheduleView, setScheduleView] = useState<'list' | 'calendar'>('list')

  // 활동 배지 재료 — 내 참석 통계와 받은 후기 통계(공개 프로필 API에서 가져온다)
  const [myAttend, setMyAttend] = useState<AttendStats>({})
  const [myReview, setMyReview] = useState<ReviewStats | null>(null)
  const myBadges = badgesOf({
    attended: num(myAttend.attendedCnt),
    noshow: num(myAttend.noshowCnt),
    hosted: recruits.length,
    reviewCnt: num(myReview?.reviewCnt),
    avgRating: num(myReview?.avgRating),
  })

  const loadBlocks = () => { blockApi.list().then(setBlocks).catch(() => {}) }
  const unblock = async (blockedHandle: string) => {
    try {
      await blockApi.toggle(blockedHandle)
      message.success('차단을 해제했습니다.')
      loadBlocks()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '해제 실패')
    }
  }

  const loadReviewTargets = () => { reviewApi.myTargets().then(setReviewTargets).catch(() => {}) }
  const loadBookmarks = () => {
    bookmarkApi.list('BBS').then(setBmPosts).catch(() => {})
    bookmarkApi.list('RECRUIT').then(setBmRecruits).catch(() => {})
  }

  /** 알림 수신 설정 토글 — 즉시 저장(낙관적 반영, 실패 시 롤백). */
  const toggleNoti = async (key: keyof NotiSetting, on: boolean) => {
    const next = { ...notiSet, [key]: on ? 'Y' : 'N' }
    setNotiSet(next)
    setNotiSaving(true)
    try {
      await notificationApi.saveSetting(next)
    } catch (e) {
      setNotiSet(notiSet) // 롤백
      message.error(e instanceof Error ? e.message : '설정 저장 실패')
    } finally {
      setNotiSaving(false)
    }
  }

  const removeBookmark = async (type: 'BBS' | 'RECRUIT', targetId: string) => {
    try {
      await bookmarkApi.toggle(type, targetId)
      message.success('북마크를 해제했습니다.')
      loadBookmarks()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '해제 실패')
    }
  }

  useEffect(() => {
    if (!loggedIn) return
    me().then((info) => {
      setUserId(info.userId ?? ''); setNickname(info.nickname ?? ''); setNickInput(info.nickname ?? '')
      setProfileFileId(info.profileFileId || undefined)
      // 배지 재료(참석 기록·받은 후기)는 공개 프로필 쪽에 있어 내 handle로 함께 가져온다
      if (info.handle) {
        getUserProfile(info.handle).then((p) => setMyAttend(p.attend ?? {})).catch(() => {})
        reviewApi.stats(info.handle).then((s) => setMyReview(s)).catch(() => {})
      }
    }).catch(() => {})
    myPosts().then(setPosts).catch(() => {})
    myRecruits().then(setRecruits).catch(() => {})
    applyApi.mine().then(setApplies).catch(() => {})
    loadReviewTargets()
    loadBookmarks()
    loadBlocks()
    notificationApi.setting().then((s) => setNotiSet(s ?? {})).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loggedIn])

  /**
   * 내 모임 일정 — 주최한 모집 + 참여 확정(APPLY02)된 모집을 합쳐 날짜순으로.
   * 별도 저장 없이 기존 데이터로 구성(모임일이 비어 있으면 제외).
   * 지난 모임까지 모두 담는다 — 캘린더는 지난 달도 넘겨보므로 여기서 자르면 과거가 빈 달이 된다.
   * 목록 뷰는 이 배열에서 '오늘 이후'만 걸러 쓴다(schedule).
   */
  const scheduleAll: ScheduleItem[] = (() => {
    const mine = recruits
      .filter((r) => r.meetDt)
      .map((r) => ({ key: `h-${r.rowId}`, id: r.rowId!, title: r.title ?? '', meetDt: r.meetDt!, region: r.region, role: '주최' as const }))
    const joined = applies
      .filter((a) => a.applyStatus === 'APPLY02' && a.meetDt)
      .map((a) => ({ key: `a-${a.rowId}`, id: a.recruitId!, title: a.recruitTitle ?? '', meetDt: a.meetDt!, region: a.region, role: '참여' as const }))
    return [...mine, ...joined].sort((x, y) => x.meetDt.localeCompare(y.meetDt))
  })()

  const today = new Date().toISOString().slice(0, 10)
  const schedule = scheduleAll.filter((s) => s.meetDt >= today)

  /** 오늘까지 남은 일수 표기(D-day). */
  const dday = (meetDt: string) => {
    const d = Math.ceil((new Date(`${meetDt}T00:00:00`).getTime() - new Date(new Date().toDateString()).getTime()) / 86400000)
    return d === 0 ? '오늘' : d > 0 ? `D-${d}` : ''
  }

  const submitReview = async () => {
    if (!reviewTarget?.recruitId || !reviewTarget?.targetHandle) return
    setReviewSaving(true)
    try {
      await reviewApi.insert(reviewTarget.recruitId, reviewTarget.targetHandle, rating, reviewText)
      message.success('후기를 등록했습니다.')
      setReviewOpen(false); setReviewText(''); setRating(5)
      loadReviewTargets()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '후기 등록 실패')
    } finally {
      setReviewSaving(false)
    }
  }

  if (!loggedIn) {
    return (
      <Card style={{ maxWidth: 480, margin: '40px auto', textAlign: 'center', borderRadius: 20 }}>
        <p>로그인 후 이용할 수 있습니다.</p>
        <Button type="primary" onClick={() => navigate('/login')} style={{ borderRadius: 14, fontWeight: 700 }}>로그인</Button>
      </Card>
    )
  }

  const saveNickname = async () => {
    const v = nickInput.trim()
    if (!v) { message.warning('닉네임을 입력하세요.'); return }
    try {
      await changeNickname(v)
      setNickname(v)
      setNickOpen(false)
      message.success('닉네임을 변경했습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '변경 실패')
    }
  }

  const savePw = async () => {
    const v = await pwForm.validateFields()
    try {
      await changePw(v.currentPw, v.newPw)
      message.success('비밀번호를 변경했습니다. 다시 로그인해 주세요.')
      setPwOpen(false)
      // 비번 변경 시 서버가 token_ver를 올려 현재 토큰 무효화 → 재로그인
      tokenStore.clear()
      navigate('/login', { replace: true })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '변경 실패')
    }
  }

  const doWithdraw = async () => {
    if (!withdrawPw) { message.warning('비밀번호를 입력하세요.'); return }
    try {
      await withdraw(withdrawPw)
      setWithdrawOpen(false)
      message.success('탈퇴가 완료되었습니다. 그동안 이용해 주셔서 감사합니다.')
      tokenStore.clear()
      navigate('/login', { replace: true })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '탈퇴 실패')
    }
  }

  const uploadPhoto = async (f: File) => {
    if (!f.type.startsWith('image/')) { message.warning('이미지 파일만 업로드할 수 있습니다.'); return }
    setUploading(true)
    try {
      const metas = await fileApi.upload([f])
      const fid = metas?.[0]?.fileId
      if (!fid) throw new Error('업로드에 실패했습니다.')
      await updateProfileImage(fid)
      setProfileFileId(fid)
      message.success('프로필 사진을 변경했습니다.')
    } catch (err) {
      message.error(err instanceof Error ? err.message : '변경 실패')
    } finally {
      setUploading(false)
    }
  }

  const removePhoto = async () => {
    try {
      await updateProfileImage()
      setProfileFileId(undefined)
      message.success('프로필 사진을 삭제했습니다.')
    } catch (err) {
      message.error(err instanceof Error ? err.message : '삭제 실패')
    }
  }

  const cancelApply = async (rowId: string) => {
    try {
      await applyApi.cancel(rowId)
      setApplies(await applyApi.mine())
    } catch (e) {
      message.error(e instanceof Error ? e.message : '취소 실패')
    }
  }

  const applyTag = (cd?: string, nm?: string) => {
    const color = cd === 'APPLY02' ? 'blue' : cd === 'APPLY03' ? 'red' : 'default'
    return <Tag color={color}>{nm ?? '대기'}</Tag>
  }

  const cardTitle = (emoji: string, text: string, count?: number) => (
    <span style={{ fontWeight: 700 }}>
      {emoji} {text}
      {count !== undefined && <Tag style={{ marginLeft: 4 }} color="purple">{count}</Tag>}
    </span>
  )

  const postCols: TableColumnsType<Bbs> = [
    { title: '게시판', dataIndex: 'bbsinfoNm', width: 130 },
    { title: '제목', render: (_, r) => r.title },
    { title: '댓글', width: 60, align: 'center', render: (_, r) => Number(r.commentCnt) || 0 },
    { title: '작성일', dataIndex: 'regDt', width: 120 },
  ]
  const recruitCols: TableColumnsType<Recruit> = [
    { title: '취미', dataIndex: 'hobbyNm', width: 110 },
    { title: '모임명', render: (_, r) => r.title },
    { title: '신청', width: 90, align: 'center', render: (_, r) => `${r.acceptedCnt ?? 0}/${r.applyCnt ?? 0}` },
    { title: '상태', width: 80, align: 'center', render: (_, r) => (r.statusCd === 'RECRUIT01' ? <Tag color="green">모집중</Tag> : <Tag>마감</Tag>) },
  ]
  const applyCols: TableColumnsType<RecruitApply> = [
    { title: '모집', render: (_, r) => r.recruitTitle },
    {
      // 대기 순번은 대기 상태에서만 의미가 있다(수락/거절되면 서버가 null로 준다)
      title: '상태', width: 120, align: 'center',
      render: (_, r) => (
        <Space size={4} wrap>
          {applyTag(r.applyStatus, r.applyStatusNm)}
          {r.applyStatus === 'APPLY01' && r.waitNo && <Tag color="orange">대기 {r.waitNo}</Tag>}
        </Space>
      ),
    },
    { title: '', width: 80, render: (_, r) => (r.applyStatus !== 'APPLY02' ? <a onClick={(e) => { e.stopPropagation(); cancelApply(r.rowId!) }}>취소</a> : null) },
  ]

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* 프로필 헤더 */}
      <div style={{ background: gen.heroTint, borderRadius: 24, padding: '24px 26px', display: 'flex', gap: 18, alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ position: 'relative', flexShrink: 0 }}>
          <div onClick={() => fileRef.current?.click()} title="프로필 사진 변경"
            style={{ width: 64, height: 64, borderRadius: '50%', background: gen.primary, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, fontWeight: 800, cursor: 'pointer', overflow: 'hidden', opacity: uploading ? 0.6 : 1 }}>
            {profileFileId
              ? <img src={`/api/pub/image/${profileFileId}`} alt="프로필" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              : (nickname || userId || '?').slice(0, 1)}
          </div>
          <div onClick={() => fileRef.current?.click()} aria-hidden
            style={{ position: 'absolute', right: -2, bottom: -2, width: 24, height: 24, borderRadius: '50%', background: '#fff', boxShadow: '0 1px 4px rgba(0,0,0,.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, cursor: 'pointer' }}>📷</div>
          <input ref={fileRef} type="file" accept="image/*" hidden
            onChange={(e) => { const f = e.target.files?.[0]; e.currentTarget.value = ''; if (f) uploadPhoto(f) }} />
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 22, fontWeight: 800, color: gen.heroText }}>{nickname || '회원'}</div>
          <div style={{ color: '#8078A8', fontSize: 13, marginTop: 2 }}>
            @{userId}
            {profileFileId && <> · <a onClick={removePhoto}>사진 삭제</a></>}
          </div>
          {/* 활동 배지 — 참석·주최·후기에서 파생(공개 프로필과 같은 기준) */}
          {myBadges.length > 0 && (
            <Space size={5} style={{ marginTop: 8 }} wrap>
              {myBadges.map((b) => (
                <Tooltip key={b.key} title={b.desc}>
                  <Tag color={b.color} style={{ fontWeight: 600, cursor: 'default' }}>{b.label}</Tag>
                </Tooltip>
              ))}
            </Space>
          )}
        </div>
        <Space style={{ marginLeft: 'auto' }} wrap>
          <Button onClick={() => navigate('/gen/myhobby')} type="primary" ghost style={{ borderRadius: 12, fontWeight: 600 }}>♥ 나의 취미</Button>
          <Button onClick={() => { setNickInput(nickname); setNickOpen(true) }} style={{ borderRadius: 12, fontWeight: 600 }}>닉네임 변경</Button>
          <Button onClick={() => { pwForm.resetFields(); setPwOpen(true) }} style={{ borderRadius: 12, fontWeight: 600 }}>비밀번호 변경</Button>
        </Space>
      </div>

      {/* 내 모임 일정 — 목록(다가오는 것만) / 캘린더(지난 달도 조회) 전환 */}
      <Card
        style={{ borderRadius: 18 }} title={cardTitle('📅', '내 모임 일정', schedule.length)}
        extra={
          <Segmented
            size="small" value={scheduleView} onChange={(v) => setScheduleView(v as 'list' | 'calendar')}
            options={[{ value: 'list', label: '목록' }, { value: 'calendar', label: '캘린더' }]}
          />
        }
      >
        {scheduleView === 'calendar' ? (
          <ScheduleCalendar items={scheduleAll} onSelect={(id) => navigate(`/gen/recruit/${id}`)} />
        ) : schedule.length === 0 ? (
          <Empty description="다가오는 모임이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {schedule.map((s) => (
              <div key={s.key}
                onClick={() => navigate(`/gen/recruit/${s.id}`)}
                style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', flexWrap: 'wrap' }}>
                <Tag color={s.role === '주최' ? 'purple' : 'blue'} style={{ flexShrink: 0 }}>{s.role}</Tag>
                <span style={{ fontWeight: 600, flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{s.title}</span>
                {s.region && <span style={{ fontSize: 12, color: '#888' }}>{s.region}</span>}
                <span style={{ fontSize: 12, color: '#666' }}>{s.meetDt}</span>
                <Tag color={dday(s.meetDt) === '오늘' ? 'red' : 'default'} style={{ flexShrink: 0, marginInlineEnd: 0 }}>{dday(s.meetDt)}</Tag>
              </div>
            ))}
          </Space>
        )}
      </Card>

      {/* 내가 쓴 글 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('📝', '내가 쓴 글', posts.length)}>
        {posts.length === 0 ? <Empty description="작성한 글이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
          <Table<Bbs> rowKey="rowId" size="small" scroll={{ x: 'max-content' }} columns={postCols} dataSource={posts} pagination={false}
            onRow={(r) => ({ onClick: () => navigate(`/gen/board/${r.bbsinfoId}?post=${r.rowId}`), style: { cursor: 'pointer' } })} />
        )}
      </Card>

      {/* 내가 연 모집 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('📣', '내가 연 모집', recruits.length)}>
        {recruits.length === 0 ? <Empty description="등록한 모집이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
          <Table<Recruit> rowKey="rowId" size="small" scroll={{ x: 'max-content' }} columns={recruitCols} dataSource={recruits} pagination={false}
            onRow={(r) => ({ onClick: () => navigate(`/gen/recruit/${r.rowId}`), style: { cursor: 'pointer' } })} />
        )}
      </Card>

      {/* 내가 신청한 모집 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('✋', '내가 신청한 모집', applies.length)}>
        {applies.length === 0 ? <Empty description="신청한 모집이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
          <Table<RecruitApply> rowKey="rowId" size="small" scroll={{ x: 'max-content' }} columns={applyCols} dataSource={applies} pagination={false}
            onRow={(r) => ({ onClick: () => navigate(`/gen/recruit/${r.recruitId}`), style: { cursor: 'pointer' } })} />
        )}
      </Card>

      {/* 북마크(스크랩) */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('🔖', '북마크', bmPosts.length + bmRecruits.length)}>
        {bmPosts.length + bmRecruits.length === 0 ? (
          <Empty description="북마크한 글·모집이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={6}>
            {bmPosts.map((b) => (
              <div key={`b-${b.rowId}`} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Tag color="cyan" style={{ flexShrink: 0 }}>{b.subNm || '게시판'}</Tag>
                <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', cursor: 'pointer' }}
                  onClick={() => navigate(`/gen/board/${b.bbsinfoId}?post=${b.targetId}`)}>{b.title}</span>
                <a style={{ fontSize: 12, color: '#999', flexShrink: 0 }} onClick={() => removeBookmark('BBS', b.targetId!)}>해제</a>
              </div>
            ))}
            {bmRecruits.map((b) => (
              <div key={`r-${b.rowId}`} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Tag color="purple" style={{ flexShrink: 0 }}>{b.subNm || '모집'}</Tag>
                <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', cursor: 'pointer' }}
                  onClick={() => navigate(`/gen/recruit/${b.targetId}`)}>{b.title}</span>
                {b.statusNm && <Tag style={{ flexShrink: 0 }}>{b.statusNm}</Tag>}
                <a style={{ fontSize: 12, color: '#999', flexShrink: 0 }} onClick={() => removeBookmark('RECRUIT', b.targetId!)}>해제</a>
              </div>
            ))}
          </Space>
        )}
      </Card>

      {/* 차단한 회원 — 차단 시 상대는 나에게 쪽지를 보낼 수 없음 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('🚫', '차단한 회원', blocks.length)}>
        {blocks.length === 0 ? (
          <Empty description="차단한 회원이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {blocks.map((b) => (
              <div key={b.rowId} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                <span style={{ fontWeight: 600, color: gen.primary, cursor: 'pointer' }} onClick={() => navigate(`/gen/user/${b.blockedHandle}`)}>
                  {b.blockedNm || '-'}
                </span>
                <Space size={8}>
                  <span style={{ fontSize: 12, color: '#aaa' }}>{b.regDt}</span>
                  <Button size="small" onClick={() => unblock(b.blockedHandle!)}>차단 해제</Button>
                </Space>
              </div>
            ))}
          </Space>
        )}
      </Card>

      {/* 알림 수신 설정 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('🔔', '알림 설정')}>
        <Space direction="vertical" style={{ width: '100%' }} size={10}>
          {([
            ['notiApply', '모집 신청·수락·거절'],
            ['notiComment', '댓글·답글'],
            ['notiMessage', '쪽지'],
            ['notiReview', '모임 후기'],
          ] as [keyof NotiSetting, string][]).map(([key, label]) => (
            <div key={key} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>{label}</span>
              <Switch
                checked={notiSet[key] !== 'N'} disabled={notiSaving}
                onChange={(on) => toggleNoti(key, on)}
                checkedChildren="수신" unCheckedChildren="끔"
              />
            </div>
          ))}
        </Space>
      </Card>

      {/* 후기 쓰기 — 종료된 모임에서 함께한 회원 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('⭐', '함께한 회원 후기', reviewTargets.filter((t) => t.writtenYn !== 'Y').length)}>
        {reviewTargets.length === 0 ? (
          <Empty description="후기를 쓸 수 있는 모임이 없습니다. (모임이 마감·종료되면 표시됩니다)" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {reviewTargets.map((t) => (
              <div key={`${t.recruitId}-${t.targetHandle}`} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                <div style={{ minWidth: 0 }}>
                  <span
                    style={{ fontWeight: 600, color: gen.primary, cursor: 'pointer' }}
                    onClick={() => navigate(`/gen/user/${t.targetHandle}`)}
                  >
                    {t.targetNm || '-'}
                  </span>
                  <span style={{ color: '#999', fontSize: 12, marginLeft: 8 }}>{t.recruitTitle}</span>
                </div>
                {t.writtenYn === 'Y'
                  ? <Tag color="green">후기 작성 완료</Tag>
                  : <Button size="small" type="primary" ghost onClick={() => { setReviewTarget(t); setRating(5); setReviewText(''); setReviewOpen(true) }}>후기 쓰기</Button>}
              </div>
            ))}
          </Space>
        )}
      </Card>

      <div style={{ textAlign: 'center', marginTop: 4 }}>
        <Button type="text" danger onClick={() => { setWithdrawPw(''); setWithdrawOpen(true) }}>회원 탈퇴</Button>
      </div>

      {/* 후기 작성 */}
      <Modal
        open={reviewOpen} title={`후기 쓰기 — ${reviewTarget?.targetNm || ''}`}
        onOk={submitReview} onCancel={() => setReviewOpen(false)}
        okText="등록" cancelText="취소" confirmLoading={reviewSaving}
      >
        <div style={{ color: '#888', fontSize: 13, marginBottom: 10 }}>모임: {reviewTarget?.recruitTitle}</div>
        <div style={{ marginBottom: 12 }}>
          <Rate value={rating} onChange={setRating} />
          <span style={{ marginLeft: 8, color: gen.primary, fontWeight: 700 }}>{rating}점</span>
        </div>
        <Input.TextArea
          value={reviewText} onChange={(e) => setReviewText(e.target.value)}
          placeholder="함께한 경험을 남겨주세요. (선택, 500자)" maxLength={500} autoSize={{ minRows: 3, maxRows: 6 }}
        />
      </Modal>

      {/* 회원 탈퇴 */}
      <Modal title="회원 탈퇴" open={withdrawOpen} onCancel={() => setWithdrawOpen(false)} onOk={doWithdraw}
        okText="탈퇴" cancelText="취소" okButtonProps={{ danger: true }}>
        <p>탈퇴하면 계정이 비활성화되어 다시 로그인할 수 없습니다. 계속하려면 현재 비밀번호를 입력하세요.</p>
        <Input.Password value={withdrawPw} onChange={(e) => setWithdrawPw(e.target.value)}
          placeholder="현재 비밀번호" autoComplete="current-password" onPressEnter={doWithdraw} />
      </Modal>

      {/* 닉네임 변경 */}
      <Modal title="닉네임 변경" open={nickOpen} onCancel={() => setNickOpen(false)} onOk={saveNickname} okText="변경" cancelText="취소">
        <Input value={nickInput} maxLength={30} onChange={(e) => setNickInput(e.target.value)} placeholder="닉네임(최대 30자)" />
      </Modal>

      {/* 비밀번호 변경 */}
      <Modal title="비밀번호 변경" open={pwOpen} onCancel={() => setPwOpen(false)} onOk={savePw} okText="변경" cancelText="취소">
        <Form form={pwForm} layout="vertical">
          <Form.Item name="currentPw" label="현재 비밀번호" rules={[{ required: true, message: '현재 비밀번호를 입력하세요.' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item name="newPw" label="새 비밀번호" rules={[{ required: true, message: '새 비밀번호를 입력하세요.' }, { min: 8, message: '8자 이상' }]}
            extra="8~64자, 영문·숫자·특수문자 포함">
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="newPwConfirm" label="새 비밀번호 확인" dependencies={['newPw']}
            rules={[{ required: true, message: '한 번 더 입력하세요.' },
              ({ getFieldValue }) => ({ validator: (_, v) => (!v || getFieldValue('newPw') === v ? Promise.resolve() : Promise.reject(new Error('비밀번호가 일치하지 않습니다.'))) })]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
