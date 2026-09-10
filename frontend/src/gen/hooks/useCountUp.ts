import { useEffect, useRef, useState } from 'react'

/** 요소가 화면에 들어왔거나 이미 지나갔는지 (Reveal과 같은 기준 — 이유는 Reveal.tsx 주석) */
function isInView(el: HTMLElement) {
  const r = el.getBoundingClientRect()
  const h = window.innerHeight || document.documentElement.clientHeight
  return r.top < h * 0.9
}

/**
 * 숫자 카운트업 — 요소가 화면에 들어오면 0에서 target까지 올라간다.
 *
 * <p>감속 곡선(easeOutCubic)을 넣은 이유는 등속으로 올리면 끝에서 갑자기 멈춰 어색하기 때문이다.
 *
 * <p>★ 트리거는 스크롤 판정, 진행은 requestAnimationFrame이다. 단 rAF가 <b>한 번도 불리지
 * 않는 환경</b>을 실측으로 만났고(프레임을 만들지 않는 임베드), 그때 숫자가 0에 갇혀
 * "0"으로 표시됐다. 그래서 타이머 안전망을 둬서 애니메이션이 안 되면 최종값이라도 보이게 했다.
 * 연출은 없어도 되지만 <b>값이 틀리게 보이는 것은 안 된다</b> — 특히 이 값들은 실제 집계다.
 *
 * <p>목표값이 나중에 도착하는 경우(조회 응답 후 갱신)도 있으므로 target이 바뀌면 다시 센다.
 *
 * @returns [표시할 값, 관찰 대상에 붙일 ref]
 */
export function useCountUp(target: number, durationMs = 1400): [number, React.RefObject<HTMLElement>] {
  const [value, setValue] = useState(0)
  const ref = useRef<HTMLElement>(null)
  const started = useRef(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    // target이 0이면 셀 것이 없다(조회 전 초기값). 다음 target 변경에서 시작한다.
    if (target <= 0) return
    started.current = false

    const run = () => {
      if (started.current) return
      started.current = true

      // 접근성: 모션을 줄이려는 사용자에겐 애니메이션 없이 최종값만 보여준다
      if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
        setValue(target)
        return
      }
      const start = performance.now()
      let done = false
      const tick = (now: number) => {
        const p = Math.min((now - start) / durationMs, 1)
        const eased = 1 - Math.pow(1 - p, 3)
        setValue(Math.round(target * eased))
        if (p < 1) requestAnimationFrame(tick)
        else done = true
      }
      requestAnimationFrame(tick)
      // rAF가 오지 않는 환경 대비 — 시간이 지나도 안 끝났으면 최종값으로 맞춘다
      window.setTimeout(() => {
        if (!done) setValue(target)
      }, durationMs + 200)
    }

    const check = () => {
      if (isInView(el)) {
        run()
        return true
      }
      return false
    }

    function onScroll() {
      if (check()) {
        window.removeEventListener('scroll', onScroll)
        window.removeEventListener('resize', onScroll)
      }
    }
    // 리스너를 먼저 등록(프레임 생성과 무관하게 동작해야 한다)
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll, { passive: true })
    check()
    const t = window.setTimeout(check, 60)

    return () => {
      window.clearTimeout(t)
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
  }, [target, durationMs])

  return [value, ref]
}
