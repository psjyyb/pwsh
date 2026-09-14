import { useEffect, useRef, useState } from 'react'
import { bannerApi } from '../../adm/banner/banner.api'
import type { Banner } from '../../adm/banner/banner.api'

/**
 * 제목 마커 해석 — `[[...]]`=강조(그라디언트), `\n`=줄바꿈.
 *
 * <p>저장값을 `dangerouslySetInnerHTML`로 렌더하면 배너 등록 권한이 곧 XSS 권한이 된다.
 * 그래서 HTML을 허용하지 않고 마커만 해석해 React 노드로 만든다(태그를 넣어도 글자로 보인다).
 */
function renderTitle(title: string) {
  return title.split('\n').map((line, li) => (
    <span key={li}>
      {li > 0 && <br />}
      {line.split(/(\[\[[^\]]*\]\])/).map((part, pi) =>
        part.startsWith('[[') && part.endsWith(']]') ? (
          <span className="gen-point" key={pi}>
            {part.slice(2, -2)}
          </span>
        ) : (
          part
        ),
      )}
    </span>
  ))
}

const AUTOPLAY_MS = 6000

/**
 * 메인 히어로 — 풀스크린 슬라이더. 내용은 관리자 > 시스템관리 > 배너관리(banner 테이블)에서 관리한다.
 *
 * <p>Swiper 같은 라이브러리를 쓰지 않는다. 가로 `overflow-x: auto` + `scroll-snap`으로
 * 정렬·터치 스와이프·관성이 브라우저에서 그대로 나오고, 이동은 `scrollTo`로 하면 된다.
 * 필요한 것이 "슬라이드 넘기기"뿐이라 라이브러리를 얹을 이유가 없다.
 *
 * <p>자동재생은 사용자가 멈출 수 있어야 한다(접근성). 탭이 백그라운드로 가면 멈추고,
 * 모션을 줄이려는 설정이면 처음부터 자동재생하지 않는다.
 *
 * <p>등록된 배너가 없어도(조회 실패 포함) 어두운 밴드 한 장은 반드시 그린다 —
 * 헤더가 이 비주얼 위에 투명하게 떠 있어서, 비어 있으면 흰 배경에 흰 글자가 된다.
 */
export default function HeroSection({
  badge,
  siteTitle,
  loggedIn,
  onNavigate,
}: {
  /** 상단 배지 문구(확장설정 main.hero-badge). 비어 있으면 배지를 그리지 않는다 */
  badge?: string
  /** 배너가 하나도 없을 때 대체로 보여줄 사이트명 */
  siteTitle?: string
  loggedIn: boolean
  onNavigate: (path: string) => void
}) {
  const trackRef = useRef<HTMLDivElement>(null)
  const [index, setIndex] = useState(0)
  const [playing, setPlaying] = useState(true)
  const [banners, setBanners] = useState<Banner[]>([])

  useEffect(() => {
    bannerApi.mainList().then(setBanners).catch(() => {})
  }, [])

  // 배너가 없을 때의 대체 한 장(사이트명만). 슬라이드 조작 UI는 숨긴다.
  const slides: Banner[] = banners.length > 0 ? banners : [{ rowId: 'empty', title: siteTitle ?? '' }]
  const multi = slides.length > 1

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

  /**
   * 이미 로그인한 사람에게 '회원가입' 버튼은 의미가 없다 — 같은 자리에서 내 피드로 보낸다.
   * 배너 내용(DB)으로는 표현할 수 없는 분기라 여기서만 처리한다.
   */
  const cta = (label: string, url: string) =>
    loggedIn && url === '/signup' ? { label: '내 피드 보기', url: '/gen/feed' } : { label, url }

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
    if (!playing || !multi) return
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
  }, [playing, multi, slides.length])

  return (
    <section className="gen-hero" aria-label="주요 소개">
      <div className="gen-hero-track" ref={trackRef}>
        {slides.map((s, i) => {
          const b1 = s.btn1Label ? cta(s.btn1Label, s.btn1Url || '/gen') : null
          const b2 = s.btn2Label ? cta(s.btn2Label, s.btn2Url || '/gen') : null
          return (
            <div
              className="gen-hero-slide"
              key={s.rowId}
              aria-hidden={i !== index}
              role="group"
              aria-label={`${i + 1} / ${slides.length}`}
            >
              <div className="gen-hero-bg">{s.fileId && <img src={`/api/pub/image/${s.fileId}`} alt="" />}</div>
              <div className="gen-hero-overlay" />
              <div className="gen-hero-txt gen-wfix">
                {badge && <span className="gen-hero-badge">{badge}</span>}
                <h2>{renderTitle(s.title ?? '')}</h2>
                {s.description && <p>{s.description}</p>}
                <div className="gen-hero-btns">
                  {b1 && (
                    <button type="button" className="gen-btn gen-btn-primary" onClick={() => activate(b1.url)}>
                      {b1.label} <span aria-hidden>→</span>
                    </button>
                  )}
                  {b2 && (
                    <button type="button" className="gen-btn gen-btn-line" onClick={() => activate(b2.url)}>
                      {b2.label}
                    </button>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {multi && (
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
      )}

      <span className="gen-hero-scroll" aria-hidden>
        SCROLL
      </span>

      {multi && (
        <div className="gen-hero-dots" aria-hidden>
          {slides.map((s, i) => (
            <span key={s.rowId} className={i === index ? 'is-on' : ''} />
          ))}
        </div>
      )}
    </section>
  )
}
