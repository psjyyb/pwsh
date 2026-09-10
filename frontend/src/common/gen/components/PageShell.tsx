import { createContext, useContext } from 'react'
import type { ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

/** 경로 → 상위 메뉴명 체인(자기 이름 포함). GenLayout이 GEN 메뉴 트리에서 만들어 넣는다. */
export type CrumbMap = Map<string, string[]>

const CrumbContext = createContext<CrumbMap>(new Map())

/**
 * 위치 내비용 메뉴 경로 공급 — GenLayout이 감싼다.
 * 페이지마다 브레드크럼을 직접 넘기게 하면 메뉴가 바뀔 때 화면들이 같이 틀어지므로,
 * 메뉴 트리를 한 곳에서 만들어 내려준다.
 */
export function CrumbProvider({ value, children }: { value: CrumbMap; children: ReactNode }) {
  return <CrumbContext.Provider value={value}>{children}</CrumbContext.Provider>
}

/**
 * 사용자(gen) 화면 공통 셸 — 어두운 비주얼 밴드(헤드) + 같은 폭의 본문.
 *
 * <p><b>왜 있나.</b> 메인만 랜딩처럼 꾸미고 나머지 화면이 제각각이면 사이트가 두 개처럼 보인다.
 * 모든 사용자 화면이 이 셸을 쓰면 분류 라벨(eyebrow) → 제목 → 설명 → 액션의 위계와 본문 폭이
 * 화면을 옮겨도 흔들리지 않는다.
 *
 * <p>헤드는 <b>메인 히어로와 같은 언어</b>다 — 어두운 그라디언트 밴드에 흰 큰 제목, 내용은 하단
 * 정렬, 그 아래 얇은 선과 위치 내비. 헤더는 이 밴드 위에 투명하게 떠 있다(GenLayout).
 *
 * <p>★ 페이지에서 `maxWidth`를 직접 쓰지 않는다. 예전에는 화면마다 720·860·900·960·1000이
 * 섞여 있어서 메뉴를 옮길 때마다 본문 폭이 튀었다. 폭은 {@link PageBody}의 `narrow`로만 고른다
 * (넓게=목록·카드 그리드, narrow=읽기 위주 화면).
 */
export function PageHead({
  eyebrow,
  title,
  lead,
  right,
  media,
  crumbs,
  children,
}: {
  /** 분류 라벨(작은 대문자 느낌). 화면이 무엇에 속하는지 한 단어로 */
  eyebrow: string
  title: ReactNode
  lead?: ReactNode
  /** 우측 액션(버튼·필터 등) */
  right?: ReactNode
  /** 좌측 비주얼(취미 썸네일·프로필 이미지) */
  media?: ReactNode
  /**
   * 위치 내비를 직접 지정할 때만. 기본값은 메뉴 트리에서 현재 경로로 찾고,
   * 못 찾으면(상세·작성처럼 메뉴에 없는 화면) 분류 라벨을 쓴다.
   */
  crumbs?: string[]
  /** 제목 아래 추가 요소(태그·메타·버튼 줄 등) */
  children?: ReactNode
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const crumbMap = useContext(CrumbContext)
  // 메뉴에 등록된 화면이면 상위 메뉴까지, 아니면 분류 라벨 한 칸
  const trail = crumbs ?? crumbMap.get(location.pathname) ?? [eyebrow]

  return (
    <div className="gen-page-head">
      <div className="gen-wfix">
        <div className="gen-page-head-row">
          <div style={{ display: 'flex', gap: 18, alignItems: 'center', minWidth: 0 }}>
            {media && <div className="gen-page-head-media">{media}</div>}
            <div style={{ minWidth: 0 }}>
              <span className="gen-eyebrow">{eyebrow}</span>
              <h2 className="gen-sec-title">{title}</h2>
              {lead && <p className="gen-lead">{lead}</p>}
              {children}
            </div>
          </div>
          {right}
        </div>

        <ul className="gen-crumb">
          <li>
            <button type="button" onClick={() => navigate('/gen')}>홈</button>
          </li>
          {trail.map((c, i) => (
            <li key={`${c}-${i}`}>{c}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}

/** 헤드 아래 본문. `narrow`는 글을 읽는 화면(피드·프로필·검색·마이페이지)에 쓴다. */
export function PageBody({ children, narrow = false }: { children: ReactNode; narrow?: boolean }) {
  return (
    <div className="gen-page">
      <div className={`gen-wfix${narrow ? ' is-narrow' : ''}`}>{children}</div>
    </div>
  )
}
