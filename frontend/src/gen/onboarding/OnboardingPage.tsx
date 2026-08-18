import { useEffect, useState } from 'react'
import { Button, Empty, Spin, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { tokenStore } from '../../auth/token'
import { hobbyApi, userHobbyApi } from '../../adm/hobby/hobby.api'
import type { Hobby } from '../../adm/hobby/hobby.api'
import { gen, hobbyColor } from '../theme'

/** 권장 선택 수 — 이 정도는 담아야 피드가 비지 않는다(강제는 아님). */
const RECOMMEND = 3

/**
 * 가입 직후 관심 취미 고르기.
 *
 * <p>담은 취미가 없으면 '내 피드'가 비고 새 모집 알림도 오지 않는다 — 가입하자마자 빈 화면을 보게 되는
 * 문제를 막는 것이 이 화면의 목적이다. 건너뛸 수 있고, 나중에 '나의 취미'에서 언제든 바꿀 수 있다.
 */
export default function OnboardingPage() {
  const navigate = useNavigate()
  const [hobbies, setHobbies] = useState<Hobby[]>([])
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!tokenStore.get()) { navigate('/login', { replace: true }); return }
    hobbyApi.listAll()
      .then((list) => setHobbies(list))
      .catch(() => message.error('취미 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [navigate])

  const toggle = (rowId: string) => {
    setPicked((prev) => {
      const next = new Set(prev)
      if (next.has(rowId)) next.delete(rowId)
      else next.add(rowId)
      return next
    })
  }

  const start = async () => {
    if (picked.size === 0) { navigate('/gen', { replace: true }); return }
    setSaving(true)
    try {
      // 하나씩 등록(담기 API가 단건). 일부 실패해도 나머지는 담기도록 개별 처리한다.
      const results = await Promise.allSettled([...picked].map((id) => userHobbyApi.save(id)))
      const ok = results.filter((r) => r.status === 'fulfilled').length
      if (ok === 0) throw new Error('취미 담기에 실패했습니다.')
      message.success(`취미 ${ok}개를 담았어요. 이제 새 글과 모집이 피드에 모입니다.`)
      navigate('/gen/feed', { replace: true })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '취미 담기 실패')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div style={{ textAlign: 'center', padding: '80px 0' }}><Spin size="large" /></div>

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      <div style={{ textAlign: 'center', marginBottom: 24 }}>
        <div style={{ fontSize: 26, fontWeight: 800, color: gen.heroText }}>어떤 취미에 관심 있으세요?</div>
        <div style={{ color: gen.inkSoft, marginTop: 8 }}>
          고른 취미의 새 글과 모집이 <b>내 피드</b>에 모이고, 새 모집이 열리면 알려드려요.
          {' '}{RECOMMEND}개 이상이면 딱 좋아요. (나중에 바꿀 수 있어요)
        </div>
      </div>

      {hobbies.length === 0 ? (
        <Empty description="등록된 취미가 없습니다." />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 12 }}>
          {hobbies.map((h) => {
            const id = h.rowId!
            const on = picked.has(id)
            const c = hobbyColor(id)
            return (
              <div
                key={id}
                onClick={() => toggle(id)}
                style={{
                  cursor: 'pointer', borderRadius: 14, padding: '18px 14px', minHeight: 96,
                  background: on ? c.gradient : gen.surface,
                  color: on ? '#fff' : gen.ink,
                  border: `2px solid ${on ? 'transparent' : gen.line}`,
                  boxShadow: on ? '0 6px 18px rgba(0,0,0,.12)' : 'none',
                  transition: 'transform .12s ease',
                  transform: on ? 'translateY(-2px)' : 'none',
                  display: 'flex', flexDirection: 'column', justifyContent: 'center',
                }}
              >
                <div style={{ fontWeight: 800, fontSize: 16 }}>{on ? '✓ ' : ''}{h.hobbyNm}</div>
                {h.summary && (
                  <div style={{ fontSize: 12, marginTop: 6, opacity: on ? 0.9 : 0.6, lineHeight: 1.4 }}>
                    {h.summary}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 12, marginTop: 28 }}>
        <Button size="large" onClick={() => navigate('/gen', { replace: true })}>나중에 할게요</Button>
        <Button size="large" type="primary" loading={saving} onClick={start} style={{ minWidth: 180, fontWeight: 700 }}>
          {picked.size > 0 ? `${picked.size}개 담고 시작하기` : '시작하기'}
        </Button>
      </div>
    </div>
  )
}
