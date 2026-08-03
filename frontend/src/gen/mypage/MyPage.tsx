import { useEffect, useRef, useState } from 'react'
import { Button, Card, Empty, Form, Input, Modal, Space, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useNavigate } from 'react-router-dom'
import { tokenStore } from '../../auth/token'
import { me, changePw, updateProfileImage, withdraw } from '../../api/auth'
import { fileApi } from '../../api/file'
import { applyApi } from '../recruit/recruit.api'
import type { Recruit, RecruitApply } from '../recruit/recruit.api'
import type { Bbs } from '../../adm/bbs/bbs.api'
import { myPosts, myRecruits, changeNickname } from './mypage.api'
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

  useEffect(() => {
    if (!loggedIn) return
    me().then((info) => { setUserId(info.userId ?? ''); setNickname(info.nickname ?? ''); setNickInput(info.nickname ?? ''); setProfileFileId(info.profileFileId || undefined) }).catch(() => {})
    myPosts().then(setPosts).catch(() => {})
    myRecruits().then(setRecruits).catch(() => {})
    applyApi.mine().then(setApplies).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loggedIn])

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

  const cancelApply = async (dbKey: string) => {
    try {
      await applyApi.cancel(dbKey)
      setApplies(await applyApi.mine())
    } catch (e) {
      message.error(e instanceof Error ? e.message : '취소 실패')
    }
  }

  const applyTag = (cd?: string, nm?: string) => {
    const color = cd === 'APPLY02' ? 'blue' : cd === 'APPLY03' ? 'red' : 'default'
    return <Tag color={color}>{nm ?? '대기'}</Tag>
  }

  const cardTitle = (emoji: string, text: string, count: number) => (
    <span style={{ fontWeight: 700 }}>{emoji} {text} <Tag style={{ marginLeft: 4 }} color="purple">{count}</Tag></span>
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
    { title: '상태', width: 90, align: 'center', render: (_, r) => applyTag(r.applyStatus, r.applyStatusNm) },
    { title: '', width: 80, render: (_, r) => (r.applyStatus !== 'APPLY02' ? <a onClick={(e) => { e.stopPropagation(); cancelApply(r.dbKey!) }}>취소</a> : null) },
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
        </div>
        <Space style={{ marginLeft: 'auto' }} wrap>
          <Button onClick={() => navigate('/gen/myhobby')} type="primary" ghost style={{ borderRadius: 12, fontWeight: 600 }}>♥ 나의 취미</Button>
          <Button onClick={() => { setNickInput(nickname); setNickOpen(true) }} style={{ borderRadius: 12, fontWeight: 600 }}>닉네임 변경</Button>
          <Button onClick={() => { pwForm.resetFields(); setPwOpen(true) }} style={{ borderRadius: 12, fontWeight: 600 }}>비밀번호 변경</Button>
        </Space>
      </div>

      {/* 내가 쓴 글 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('📝', '내가 쓴 글', posts.length)}>
        {posts.length === 0 ? <Empty description="작성한 글이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
          <Table<Bbs> rowKey="dbKey" size="small" scroll={{ x: 'max-content' }} columns={postCols} dataSource={posts} pagination={false}
            onRow={(r) => ({ onClick: () => navigate(`/gen/board/${r.bbsinfoId}?post=${r.dbKey}`), style: { cursor: 'pointer' } })} />
        )}
      </Card>

      {/* 내가 연 모집 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('📣', '내가 연 모집', recruits.length)}>
        {recruits.length === 0 ? <Empty description="등록한 모집이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
          <Table<Recruit> rowKey="dbKey" size="small" scroll={{ x: 'max-content' }} columns={recruitCols} dataSource={recruits} pagination={false}
            onRow={(r) => ({ onClick: () => navigate(`/gen/recruit/${r.dbKey}`), style: { cursor: 'pointer' } })} />
        )}
      </Card>

      {/* 내가 신청한 모집 */}
      <Card style={{ borderRadius: 18 }} title={cardTitle('✋', '내가 신청한 모집', applies.length)}>
        {applies.length === 0 ? <Empty description="신청한 모집이 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
          <Table<RecruitApply> rowKey="dbKey" size="small" scroll={{ x: 'max-content' }} columns={applyCols} dataSource={applies} pagination={false}
            onRow={(r) => ({ onClick: () => navigate(`/gen/recruit/${r.recruitId}`), style: { cursor: 'pointer' } })} />
        )}
      </Card>

      <div style={{ textAlign: 'center', marginTop: 4 }}>
        <Button type="text" danger onClick={() => { setWithdrawPw(''); setWithdrawOpen(true) }}>회원 탈퇴</Button>
      </div>

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
