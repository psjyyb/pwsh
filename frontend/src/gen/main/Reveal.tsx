import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'

/** 요소가 화면에 들어왔거나 이미 지나갔는지 */
function isInView(el: HTMLElement) {
  const r = el.getBoundingClientRect()
  const h = window.innerHeight || document.documentElement.clientHeight
  /*
    아래에서 살짝 올라온 뒤 시작해야 자연스럽다 → top이 화면 하단 95%선을 넘어오면 표시.
    ★ "화면 밖으로 위로 지나갔는가"는 보지 않는다(`r.bottom > 0`을 조건에 넣으면 안 된다).
      넣었다가 실측으로 잡은 결함: 앵커 이동(#recruit)이나 새로고침 시 스크롤 위치 복원처럼
      **한 번에 점프**하면 중간 요소들을 지나쳐 버리고, 그 요소들은 판정 시점에 이미
      bottom <= 0이라 영구히 opacity:0으로 남는다. 다시 위로 올라가면 빈 화면을 보게 된다.
      지나간 요소는 '이미 본 것'으로 즉시 표시하는 편이 안전하다.
  */
  return r.top < h * 0.95
}

/**
 * 스크롤 등장 연출 래퍼 — 화면에 들어오면 자식을 페이드·슬라이드로 보여준다.
 *
 * <p>라이브러리(AOS 등)를 쓰지 않는다. 하는 일이 "보이면 클래스 추가"뿐이다.
 *
 * <p>★ 왜 IntersectionObserver가 아니라 스크롤 판정인가 — 바탕 CMS에서 두 번 갈아탄 끝의
 * 결론을 그대로 가져왔다. IO 콜백이 <b>아예 오지 않는 환경</b>을 실측했고(observe 후 800ms 동안
 * 0회), 그때 {@code opacity: 0}인 콘텐츠가 영구히 보이지 않았다 — 화면이 백지가 되는 실패다.
 * 그래서 브라우저 기능에 기대지 않고 rect를 직접 재는 방식으로 갔다.
 * 마운트 시 한 번 판정하고, 스크롤에만 다시 확인하며, 한 번 보이면 리스너를 뗀다(비용 최소).
 *
 * <p>이 프로젝트는 목록 진입 애니메이션으로 {@code .gen-rise}(CSS 키프레임)를 쓰는데, 그건
 * "렌더되면 무조건 1회"라 스크롤 아래 요소도 안 보이는 동안 끝나버린다. 랜딩처럼 화면을 내려가며
 * 순서대로 보여줘야 하는 곳에는 이 컴포넌트를 쓴다.
 *
 * @param dir   들어오는 방향. up=아래에서, left/right=옆에서, fade=제자리 페이드
 * @param delay 지연(ms). 같은 줄의 카드들을 순차로 띄울 때 쓴다
 */
export default function Reveal({
  children,
  dir = 'up',
  delay = 0,
  className,
  as: Tag = 'div',
  onClick,
}: {
  children: ReactNode
  dir?: 'up' | 'left' | 'right' | 'fade'
  delay?: number
  className?: string
  /** 감싸는 태그. 목록 안에서 쓸 땐 'ul'/'article' 등으로 의미에 맞게 준다 */
  as?: 'div' | 'section' | 'article' | 'ul'
  onClick?: () => void
}) {
  // 동적 태그에 ref를 넘기려면 구체 타입(HTMLUListElement 등)이 서로 안 맞아
  // ElementType으로 받아 느슨하게 둔다(태그가 런타임 값이라 정적 추론이 불가능한 자리다)
  const Wrapper = Tag as React.ElementType
  const ref = useRef<HTMLElement>(null)
  const [shown, setShown] = useState(false)

  useEffect(() => {
    if (shown) return
    const el = ref.current
    if (!el) return

    const check = () => {
      if (isInView(el)) {
        setShown(true)
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

    // ★ 리스너를 먼저 등록한다. requestAnimationFrame 안에서 등록했더니, 프레임을 만들지
    //   않는 환경(실측: rAF가 500ms 동안 0회)에서 리스너조차 붙지 않아 콘텐츠가 영구히
    //   안 보였다. 등록은 무조건 하고, 초기 판정만 뒤로 미룬다.
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll, { passive: true })

    // 첫 화면에 이미 들어와 있는 요소는 스크롤 없이도 보여야 한다.
    // 즉시 한 번, 레이아웃이 잡힌 뒤 한 번 더 본다(타이머는 프레임 생성과 무관하게 돈다).
    check()
    const t = window.setTimeout(check, 60)
    return () => {
      window.clearTimeout(t)
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
  }, [shown])

  const cls = ['gen-reveal', `is-${dir}`, shown ? 'is-in' : '', className].filter(Boolean).join(' ')

  return (
    <Wrapper ref={ref} className={cls} style={{ transitionDelay: `${delay}ms` }} onClick={onClick}>
      {children}
    </Wrapper>
  )
}
