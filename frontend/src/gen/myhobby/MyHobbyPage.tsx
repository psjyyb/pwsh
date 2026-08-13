import { useEffect, useState } from 'react'
import { Button, Card, Col, Empty, Row, Tag, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { tokenStore } from '../../auth/token'
import CodeSelect from '../../common/adm/components/CodeSelect'
import { hobbyApi, userHobbyApi } from '../../adm/hobby/hobby.api'
import type { Hobby, UserHobby } from '../../adm/hobby/hobby.api'
import { gen, hobbyColor } from '../theme'

/**
 * 나의 취미(/gen/myhobby) — 내가 담은 취미만 모아 보고, 각 취미의 게시판·모집으로 바로 이동.
 * 담기(관심)=레벨 없이도 가능, 레벨은 선택. 로그인 필요.
 */
export default function MyHobbyPage() {
  const navigate = useNavigate()
  const loggedIn = !!tokenStore.get()
  const [hobbies, setHobbies] = useState<Hobby[]>([])
  const [myHobbies, setMyHobbies] = useState<UserHobby[]>([])

  const loadMine = () => userHobbyApi.list().then(setMyHobbies).catch(() => {})

  useEffect(() => {
    if (!loggedIn) return
    hobbyApi.listAll().then(setHobbies).catch(() => {})
    loadMine()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loggedIn])

  if (!loggedIn) {
    return (
      <Card style={{ maxWidth: 480, margin: '40px auto', textAlign: 'center', borderRadius: 20 }}>
        <p>로그인 후 이용할 수 있습니다.</p>
        <Button type="primary" onClick={() => navigate('/login')}>로그인</Button>
      </Card>
    )
  }

  const setLevel = async (hobbyId: string, levelCd?: string) => {
    try {
      await userHobbyApi.save(hobbyId, levelCd) // 레벨 지정/해제(담기는 유지)
      await loadMine()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '레벨 저장 실패')
    }
  }

  const unregister = async (hobbyId: string) => {
    try {
      await userHobbyApi.remove(hobbyId)
      await loadMine()
      message.success('담기를 취소했습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '담기 취소 실패')
    }
  }

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* 헤더 */}
      <div style={{ background: gen.heroTint, borderRadius: 24, padding: '24px 26px' }}>
        <div style={{ fontSize: 13, color: gen.primary, fontWeight: 700 }}>♥ MY HOBBIES</div>
        <div style={{ fontSize: 24, fontWeight: 800, color: gen.heroText, marginTop: 4 }}>나의 취미</div>
        <div style={{ fontSize: 14, color: '#7A72A8', marginTop: 6 }}>담아둔 취미의 게시판과 모집을 한곳에서 확인하세요.</div>
      </div>

      {myHobbies.length === 0 ? (
        <Card style={{ borderRadius: 20 }}>
          <Empty description="아직 담은 취미가 없습니다. 마음에 드는 취미를 담아보세요." image={Empty.PRESENTED_IMAGE_SIMPLE}>
            <Button type="primary" onClick={() => navigate('/gen/main')} style={{ borderRadius: 14, fontWeight: 700 }}>취미 둘러보기</Button>
          </Empty>
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {myHobbies.map((u) => {
            const hb = hobbies.find((h) => h.dbKey === u.hobbyId)
            const hc = hobbyColor(u.hobbyId) // 메인 도감과 같은 색(취미별 고정)
            return (
              <Col xs={24} sm={12} key={u.hobbyId}>
                <Card styles={{ body: { padding: 16 } }} style={{ borderRadius: 18, height: '100%' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <div style={{ width: 48, height: 48, borderRadius: 14, flexShrink: 0, overflow: 'hidden', color: '#fff', fontSize: 20, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center', background: hc.solid }}>
                      {hb?.thumbId
                        ? <img src={`/api/pub/image/${hb.thumbId}`} alt={u.hobbyNm} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                        : (u.hobbyNm ?? '').slice(0, 1)}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 16, fontWeight: 700, cursor: 'pointer' }} onClick={() => navigate(`/gen/hobby/${u.hobbyId}`)}>{u.hobbyNm}</div>
                      <div style={{ marginTop: 4 }}>
                        {u.levelNm ? <Tag color="purple">{u.levelNm}</Tag> : <Tag>관심</Tag>}
                      </div>
                    </div>
                    <Button type="text" aria-label="담기 취소" onClick={() => unregister(u.hobbyId!)} style={{ color: '#bbb', fontSize: 16 }}>✕</Button>
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 14, flexWrap: 'wrap', alignItems: 'center' }}>
                    {hb?.bbsinfoId && (
                      <Button size="small" type="primary" ghost onClick={() => navigate(`/gen/board/${hb.bbsinfoId}`)} style={{ borderRadius: 10, fontWeight: 600 }}>게시판</Button>
                    )}
                    <Button size="small" onClick={() => navigate(`/gen/recruit?hobby=${u.hobbyId}`)} style={{ borderRadius: 10, fontWeight: 600 }}>모집</Button>
                    <CodeSelect pCodeId="HOBBYLV00" placeholder="레벨" allowClear size="small" style={{ width: 128, marginLeft: 'auto' }}
                      value={u.levelCd} onChange={(v?: string) => setLevel(u.hobbyId!, v)} />
                  </div>
                </Card>
              </Col>
            )
          })}
        </Row>
      )}
    </div>
  )
}
