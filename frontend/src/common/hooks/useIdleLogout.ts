import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { logout as authLogout } from '../../api/auth'

/**
 * 유휴 자동 로그아웃 + 만료 임박 경고.
 * - minutes분 동안 활동(mousemove/mousedown/keydown/scroll/touchstart)이 없으면 로그아웃(→/login).
 * - 로그아웃 warnMinutes분 전 경고 모달 + 남은 초 카운트다운.
 * - 경고 전 활동 시 마감시각(deadline) 리셋. 경고가 뜬 뒤에는 passive 활동으로 리셋하지 않고
 *   [계속 이용](extend) 또는 카운트다운 종료 시 로그아웃.
 * - minutes<=0 이면 비활성. 값은 t_config.session_expire_cnt에서 공급.
 *
 * 단일 interval + deadline(절대 시각) 방식 — 다중 타이머 고아 문제 없이 견고.
 * 반환: { warningOpen, remainingSec, extend(연장), logoutNow(즉시 로그아웃) }
 */
export function useIdleLogout(minutes: number, warnMinutes = 1) {
  const navigate = useNavigate()
  const [warningOpen, setWarningOpen] = useState(false)
  const [remainingSec, setRemainingSec] = useState(0)
  const deadlineRef = useRef(0)
  const warnOpenRef = useRef(false)

  const reset = useCallback(() => {
    if (!minutes || minutes <= 0) return
    deadlineRef.current = Date.now() + minutes * 60 * 1000
    warnOpenRef.current = false
    setWarningOpen(false)
  }, [minutes])

  const logoutNow = useCallback(() => {
    // 서버 token_ver 증가(토큰 무효화) + 로컬 정리 후 이동. 실패해도 authLogout이 로컬 정리 보장.
    void authLogout().finally(() => navigate('/login', { replace: true }))
  }, [navigate])

  useEffect(() => {
    if (!minutes || minutes <= 0) return
    const warnMs = Math.min(warnMinutes, minutes) * 60 * 1000
    reset()

    const onActivity = () => {
      if (!warnOpenRef.current) reset()
    }
    const events: (keyof WindowEventMap)[] = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart']
    events.forEach((e) => window.addEventListener(e, onActivity, { passive: true }))

    const tick = window.setInterval(() => {
      const left = deadlineRef.current - Date.now()
      if (left <= 0) {
        window.clearInterval(tick)
        logoutNow()
        return
      }
      if (left <= warnMs) {
        warnOpenRef.current = true
        setWarningOpen(true)
        setRemainingSec(Math.ceil(left / 1000))
      }
    }, 1000)

    return () => {
      window.clearInterval(tick)
      events.forEach((e) => window.removeEventListener(e, onActivity))
    }
  }, [minutes, warnMinutes, reset, logoutNow])

  return { warningOpen, remainingSec, extend: reset, logoutNow }
}
