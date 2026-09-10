import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'

/**
 * 히어로 슬라이드 정의.
 *
 * ★ 여기가 문구 교체 지점이다. 관리자 화면에서 배너를 관리하려면 별도 테이블이 필요하므로
 *   지금은 코드 상수로 둔다(상단 배지 문구만 확장설정 `main.hero-badge`에서 온다).
 *
 * 링크가 `#id`면 그 섹션으로 스크롤한다 — 메인 안에서 이동하는 CTA(도감 보기)는 라우팅이
 * 아니라 스크롤이어야 자연스럽다.
 */
interface Slide {
  tab: string
  title: ReactNode
  desc: string
  /** 배경 이미지 URL(없으면 CSS 기본 그라디언트) */
  bg?: string
  primary?: { label: string; href: string }
  line?: { label: string; href: string }
}

/** 마지막 슬라이드의 CTA가 로그인 여부로 갈리므로 함수로 만든다. */
function slidesOf(loggedIn: boolean): Slide[] {
  return [
    {
      tab: '도감',
      title: (
        <>
          관심사가 같은 사람들과
          <br />
          <span className="gen-point">취미로 만나요</span>
        </>
      ),
      desc: '등산부터 보드게임까지, 취미 도감에서 마음에 드는 것을 고르세요.\n같은 취미를 가진 사람들의 이야기가 기다리고 있습니다.',
      primary: { label: '취미 도감 보기', href: '#collection' },
      line: { label: '모집 둘러보기', href: '/gen/recruit' },
    },
    {
      tab: '모집',
      title: (
        <>
          혼자 하던 취미,
          <br />
          이제 <span className="gen-point">함께</span>
        </>
      ),
      desc: '모임을 열고 신청을 받고, 확정된 사람들끼리 단체 대화까지.\n약속을 잡는 과정이 한 화면에서 끝납니다.',
      primary: { label: '모집 둘러보기', href: '/gen/recruit' },
      line: { label: '지금 뭐가 열렸나', href: '#recruit' },
    },
    {
      tab: '기록',
      title: (
        <>
          참여가 쌓이면
          <br />
          <span className="gen-point">내 기록</span>이 됩니다
        </>
      ),
      desc: '참석 기록과 후기, 활동 배지로 취미 이력이 남습니다.\n다음 일정은 마이페이지 캘린더에서 한눈에 보세요.',
      primary: loggedIn
        ? { label: '내 피드 보기', href: '/gen/feed' }
        : { label: '회원가입', href: '/signup' },
    },
  ]
}

const AUTOPLAY_MS = 6000

/**
 * 메인 히어로 — 풀스크린 슬라이더.
 *
 * <p>Swiper 같은 라이브러리를 쓰지 않는다. 가로 `overflow-x: auto` + `scroll-snap`으로
 * 정렬·터치 스와이프·관성이 브라우저에서 그대로 나오고, 이동은 `scrollTo`로 하면 된다.
 * 필요한 것이 "슬라이드 넘기기"뿐이라 라이브러리를 얹을 이유가 없다.
 *
 * <p>자동재생은 사용자가 멈출 수 있어야 한다(접근성). 탭이 백그라운드로 가면 멈추고,
 * 모션을 줄이려는 설정이면 처음부터 자동재생하지 않는다.
 */
export default function HeroSection({
  badge,
  loggedIn,
  onNavigate,
}: {
  /** 상단 배지 문구(확장설정 main.hero-badge). 비어 있으면 배지를 그리지 않는다 */
  badge?: string
  loggedIn: boolean
  onNavigate: (path: string) => void
}) {
  const trackRef = useRef<HTMLDivElement>(null)
  const [index, setIndex] = useState(0)
  const [playing, setPlaying] = useState(true)
  const slides = slidesOf(loggedIn)

  const goTo = (i: number) => {
    const track = trackRef.current
    if (!track) return
    const next = (i + slides.length) % slides.length
    track.scrollTo({ left: track.clientWidth * next, behavior: 'smooth' })
  }

  /** '#id'는 같은 화면의 섹션으로 스크롤, 그 외는 라우팅 */
  const activate = (href: string) => {
    if (href.startsWith('#')) {
      document.getElementById(href.slice(1))?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      return
    }
    onNavigate(href)
  }

  // 스크롤 위치 → 현재 인덱스(사용자가 손으로 스와이프해도 표시가 맞는다)
  useEffect(() => {
    const track = trackRef.current
    if (!track) return
    const onScroll = () => {
      const i = Math.round(track.scrollLeft / track.clientWidth)
      setIndex(Math.max(0, Math.min(slides.length - 1, i)))
    }
    track.addEventListener('scroll', onScroll, { passive: true })
    return () => track.removeEventListener('scroll', onScroll)
  }, [slides.length])

  // 모션 최소화 설정이면 자동재생을 아예 시작하지 않는다
  useEffect(() => {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) setPlaying(false)
  }, [])

  useEffect(() => {
    if (!playing) return
    const id = window.setInterval(() => {
      // 탭이 숨겨져 있으면 넘기지 않는다(돌아왔을 때 여러 장 건너뛴 것처럼 보이는 것 방지)
      if (document.hidden) return
      setIndex((cur) => {
        const next = (cur + 1) % slides.length
        const track = trackRef.current
        if (track) track.scrollTo({ left: track.clientWidth * next, behavior: 'smooth' })
        return next
      })
    }, AUTOPLAY_MS)
    return () => window.clearInterval(id)
  }, [playing, slides.length])

  return (
    <section className="gen-hero" aria-label="주요 소개">
      <div className="gen-hero-track" ref={trackRef}>
        {slides.map((s, i) => (
          <div
            className="gen-hero-slide"
            key={s.tab}
            aria-hidden={i !== index}
            role="group"
            aria-label={`${i + 1} / ${slides.length} ${s.tab}`}
          >
            <div className="gen-hero-bg">{s.bg && <img src={s.bg} alt="" />}</div>
            <div className="gen-hero-overlay" />
            <div className="gen-hero-txt gen-wfix">
              {badge && <span className="gen-hero-badge">{badge}</span>}
              <h2>{s.title}</h2>
              <p>{s.desc}</p>
              <div className="gen-hero-btns">
                {s.primary && (
                  <button type="button" className="gen-btn gen-btn-primary" onClick={() => activate(s.primary!.href)}>
                    {s.primary.label} <span aria-hidden>→</span>
                  </button>
                )}
                {s.line && (
                  <button type="button" className="gen-btn gen-btn-line" onClick={() => activate(s.line!.href)}>
                    {s.line.label}
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="gen-hero-ctrl">
        <span className="gen-hero-count">
          <b>{String(index + 1).padStart(2, '0')}</b> / {String(slides.length).padStart(2, '0')}
        </span>
        <button type="button" aria-label="이전 슬라이드" onClick={() => goTo(index - 1)}>
          ‹
        </button>
        <button type="button" aria-label="다음 슬라이드" onClick={() => goTo(index + 1)}>
          ›
        </button>
        <button
          type="button"
          aria-label={playing ? '자동재생 일시정지' : '자동재생 시작'}
          onClick={() => setPlaying((p) => !p)}
        >
          {playing ? '❙❙' : '▶'}
        </button>
      </div>

      <span className="gen-hero-scroll" aria-hidden>
        SCROLL
      </span>

      <div className="gen-hero-dots" aria-hidden>
        {slides.map((s, i) => (
          <span key={s.tab} className={i === index ? 'is-on' : ''} />
        ))}
      </div>
    </section>
  )
}
