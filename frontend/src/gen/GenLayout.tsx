import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, ConfigProvider, Dropdown, Drawer, Grid, Input, Layout, Menu, Modal, Popover, Space } from 'antd'
import type { MenuProps } from 'antd'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { genTheme, gen } from './theme'
import { tokenStore, isAdmin } from '../auth/token'
import { logout as authLogout } from '../api/auth'
import { configApi } from '../adm/config/config.api'
import { pubConfigItemApi } from '../adm/configitem/configitem.api'
import { menuApi } from '../adm/menu/menu.api'
import type { Menu as MenuVO } from '../adm/menu/menu.api'
import { useIdleLogout } from '../common/hooks/useIdleLogout'
import { useDocumentTitle } from '../common/hooks/useDocumentTitle'
import { genScreens } from './genScreens'
import GenMain from './GenMain'
import GenPageView from './GenPageView'
import NotFound from '../common/NotFound'
import defaultLogo from '../assets/logo.svg'
import hobbyPattern from '../assets/hobby-pattern.svg'
import MenuGlyph from '../common/adm/components/MenuGlyph'
import PolicyViewModal from '../common/gen/components/PolicyViewModal'
import { CrumbProvider, PageBody } from '../common/gen/components/PageShell'
import { policyApi } from '../adm/policy/policy.api'
import type { Policy } from '../adm/policy/policy.api'
import { useEventStream } from '../common/gen/useEventStream'
import { notificationApi } from '../api/notification'
import { messageApi } from '../api/message'
import type { Noti } from '../api/notification'
import './gen.css'

type MenuItem = Required<MenuProps>['items'][number]

/** 헤더 높이(px). gen.css의 --gen-header-h와 반드시 같아야 한다 — 모든 화면에서 이 값만큼 콘텐츠를
 *  끌어올려 히어로가 헤더 뒤로 들어간다(둘이 어긋나면 히어로 위에 흰 띠가 생긴다). */
const HEADER_H = 72

/** 데스크톱 헤더용 커스텀 nav 노드(아이콘↑/라벨↓). */
type NavNode = { key: string; label: string; iconKey: string; dest?: string; children?: NavNode[] }

/** GEN 메뉴엔 아이콘 시드가 없어(icon CASE는 ADM만) 경로/이름으로 적절한 MenuGlyph 키를 유추. m.icon이 있으면 우선. */
function iconFor(m: MenuVO): string {
  if (m.icon) return m.icon
  const u = m.linkUrl || ''
  const nm = m.menuName || ''
  if (u.includes('/mypage')) return 'user'
  if (u.includes('/recruit')) return 'group'
  if (u.includes('/main')) return 'home'
  if (nm.includes('공지')) return 'bell'
  if (nm.includes('고객') || nm.includes('FAQ') || nm.includes('도움')) return 'help'
  if (nm.includes('문의')) return 'mail'
  if (m.connCd === 'MENU02') return 'board'
  if (m.connCd === 'MENU03') return 'page'
  return 'grid'
}

/** 플랫 메뉴(menu GEN) → 데스크톱 커스텀 nav 트리(아이콘 포함). */
function toNav(list: MenuVO[]): NavNode[] {
  const byParent = new Map<string, MenuVO[]>()
  for (const m of list) {
    const p = m.pMenuId ?? '0'
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p)!.push(m)
  }
  const build = (parentId: string): NavNode[] =>
    (byParent.get(parentId) ?? []).map((m) => {
      const children = byParent.get(m.rowId!)
      const base = { key: `n${m.rowId}`, label: m.menuName ?? '', iconKey: iconFor(m) }
      if (children && children.length) return { ...base, children: build(m.rowId!) }
      return { ...base, dest: targetOf(m) || undefined }
    })
  return build('0')
}

/** conn_cd별 이동 경로. 페이지→/gen/page/{connId}, URL→link_url, 게시판→/gen/board/{connId}, 그룹→없음 */
function targetOf(m: MenuVO): string | null {
  if (m.connCd === 'MENU03') return m.connId ? `/gen/page/${m.connId}` : null
  if (m.connCd === 'MENU02') return m.connId ? `/gen/board/${m.connId}` : null
  if (m.connCd === 'MENU01') return m.linkUrl || null
  return null
}

/** 플랫 메뉴(menu GEN) → AntD 계층 메뉴. 자식 있으면 하위메뉴, 아니면 conn_cd 목적지를 key로 */
function buildItems(list: MenuVO[]): MenuItem[] {
  const byParent = new Map<string, MenuVO[]>()
  for (const m of list) {
    const p = m.pMenuId ?? '0'
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p)!.push(m)
  }
  const build = (parentId: string): MenuItem[] =>
    (byParent.get(parentId) ?? []).map((m) => {
      const children = byParent.get(m.rowId!)
      if (children && children.length) {
        return { key: `g${m.rowId}`, label: m.menuName, children: build(m.rowId!) }
      }
      return { key: targetOf(m) || `m${m.rowId}`, label: m.menuName }
    })
  return build('0')
}

/** 경로 → 메뉴명 맵(문서 제목/현재 메뉴 표시용) */
function labelMap(list: MenuVO[]): Map<string, string> {
  const map = new Map<string, string>()
  list.forEach((m) => {
    const t = targetOf(m)
    if (t) map.set(t, m.menuName ?? '')
  })
  return map
}

/**
 * 경로 → 상위 메뉴명 체인(자기 이름 포함). 헤드 밴드의 위치 내비가 쓴다.
 * 예: 고객센터 > FAQ. 부모가 그룹(MENU04)이라 목적지가 없어도 이름은 남긴다.
 */
function crumbMap(list: MenuVO[]): Map<string, string[]> {
  const byId = new Map<string, MenuVO>()
  list.forEach((m) => byId.set(m.rowId!, m))
  const map = new Map<string, string[]>()
  list.forEach((m) => {
    const t = targetOf(m)
    if (!t) return
    const chain: string[] = []
    let cur: MenuVO | undefined = m
    // 부모를 따라 올라가며 이름을 쌓는다(순환 데이터에도 멈추도록 깊이 제한)
    for (let i = 0; cur && i < 5; i++) {
      chain.unshift(cur.menuName ?? '')
      cur = cur.pMenuId ? byId.get(cur.pMenuId) : undefined
    }
    map.set(t, chain.filter(Boolean))
  })
  return map
}

/**
 * 사용자(gen) 공통 레이아웃 — 동적 메뉴(menu area=GEN) + conn_cd 기반 라우팅.
 * 페이지관리에서 만든 콘텐츠를 메뉴(연결유형=페이지)로 연결하면 코드 수정 없이 노출됨(GenPageView).
 * 데스크톱=수평 메뉴, 모바일(md 미만)=햄버거+Drawer. 사이트명/문서제목은 환경설정(config.title) 연동.
 */
export default function GenLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.md
  const [items, setItems] = useState<MenuItem[]>([])
  const [nav, setNav] = useState<NavNode[]>([])
  const [labels, setLabels] = useState<Map<string, string>>(new Map())
  const [crumbs, setCrumbs] = useState<Map<string, string[]>>(new Map())  // 헤드 위치 내비용
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo
  const [idleMinutes, setIdleMinutes] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [notiUnread, setNotiUnread] = useState(0)
  const [msgUnread, setMsgUnread] = useState(0)
  const [notiList, setNotiList] = useState<Noti[]>([])
  const [notiOpen, setNotiOpen] = useState(false)
  const [policies, setPolicies] = useState<Policy[]>([])            // 푸터 약관 링크
  const [viewPolicyId, setViewPolicyId] = useState<string | undefined>()
  // 확장설정 site.footer-text. 조회 전·실패 시엔 기존 문구를 그대로 쓴다(푸터가 비지 않게)
  const [footerTemplate, setFooterTemplate] = useState('© {year} {title}. All rights reserved.')
  const [solid, setSolid] = useState(false)   // 스크롤로 헤더가 유리면으로 굳었는지
  const [showTop, setShowTop] = useState(false)
  const loggedIn = !!tokenStore.get() // 비로그인(게스트)도 /gen 접근 가능 — 메뉴는 GUEST 권한그룹 기준
  const { warningOpen, remainingSec, extend, logoutNow } = useIdleLogout(loggedIn ? idleMinutes : 0)

  useEffect(() => {
    menuApi
      .tree('GEN')
      .then((list) => {
        setItems(buildItems(list))
        setNav(toNav(list))
        setLabels(labelMap(list))
        setCrumbs(crumbMap(list))
      })
      .catch(() => {
        /* 메뉴 미시드 시 빈 메뉴 */
      })
    policyApi.publicList().then(setPolicies).catch(() => setPolicies([]))
    // 공개 확장설정의 푸터 문구. 조회 실패·미설정이면 기본 문구로 둔다
    pubConfigItemApi
      .list()
      .then((rows) => {
        const found = rows.find((r) => r.configKey === 'site.footer-text')
        if (found) setFooterTemplate(found.value ?? '')
      })
      .catch(() => {})
    configApi
      .view()
      .then((c) => {
        setIdleMinutes(Number(c.sessionExpireMins) || 30)
        if (c.title) setSiteTitle(c.title)
        setLogoFileId(c.logoFileId ?? undefined)
      })
      .catch(() => setIdleMinutes(30))
    if (loggedIn) {
      notificationApi.unreadCnt().then(setNotiUnread).catch(() => {})
      messageApi.unreadCnt().then(setMsgUnread).catch(() => {})
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** 안 읽음 배지 갱신(알림·쪽지) — 푸시 수신 시/폴백 주기에 호출. */
  const refreshBadges = useCallback(() => {
    if (!loggedIn) return
    notificationApi.unreadCnt().then(setNotiUnread).catch(() => {})
    messageApi.unreadCnt().then(setMsgUnread).catch(() => {})
  }, [loggedIn])

  // 서버 푸시(SSE): 새 알림·쪽지가 생기면 배지를 즉시 갱신
  const streamed = useEventStream('/api/adm/message/selectMessageListStream.do', () => refreshBadges())

  /*
    폴백 폴링 — SSE가 끊겼을 때만 동작(연결 중이면 요청하지 않는다).
    헤더는 모든 화면에 떠 있어 주기를 넉넉히(30초) 두고, 탭이 백그라운드면 건너뛴다.
  */
  useEffect(() => {
    if (!loggedIn || streamed) return
    const tick = () => {
      if (document.visibilityState !== 'visible') return
      refreshBadges()
    }
    const id = window.setInterval(tick, 30000)
    return () => window.clearInterval(id)
  }, [loggedIn, streamed, refreshBadges])

  /*
    스크롤 위치 → 헤더 굳힘 / TOP 버튼 노출.
    메인 최상단에서만 헤더가 투명(.is-hero)이고, 조금이라도 내리면 원래 유리면으로 돌아온다 —
    히어로를 벗어나면 글자색 기준이 바뀌어 투명 헤더가 읽히지 않기 때문이다.
  */
  useEffect(() => {
    const onScroll = () => {
      setSolid(window.scrollY > 40)
      setShowTop(window.scrollY > 500)
    }
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  // 경로가 바뀌면 맨 위로(메뉴 이동 후 스크롤이 중간에 남는 것 방지)
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [location.pathname])

  const openNoti = async (open: boolean) => {
    setNotiOpen(open)
    if (open) {
      try {
        setNotiList(await notificationApi.list())
        setNotiUnread(await notificationApi.unreadCnt())
      } catch { /* 무시 */ }
    }
  }
  const clickNoti = async (n: Noti) => {
    setNotiOpen(false)
    if (n.readYn !== 'Y') {
      try { await notificationApi.read(n.rowId!); setNotiUnread((u) => Math.max(0, u - 1)) } catch { /* 무시 */ }
    }
    if (n.linkUrl) navigate(n.linkUrl)
  }
  const readAllNoti = async () => {
    try {
      await notificationApi.readAll()
      setNotiUnread(0)
      setNotiList((l) => l.map((n) => ({ ...n, readYn: 'Y' })))
    } catch { /* 무시 */ }
  }
  const notificationContent = (
    <div style={{ width: 300, maxHeight: 380, overflowY: 'auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
        <b>알림</b>
        {notiList.some((n) => n.readYn !== 'Y') && <a onClick={readAllNoti}>모두 읽음</a>}
      </div>
      {notiList.length === 0 ? (
        <div style={{ color: '#999', padding: '16px 0', textAlign: 'center' }}>새 알림이 없어요</div>
      ) : (
        notiList.map((n) => (
          <div key={n.rowId} onClick={() => clickNoti(n)}
            style={{ padding: '8px 8px', marginTop: 4, borderRadius: 8, cursor: 'pointer', background: n.readYn === 'Y' ? '#fff' : '#f2ecff' }}>
            <div style={{ fontSize: 13, color: '#333' }}>{n.content}</div>
            <div style={{ fontSize: 11, color: '#aaa', marginTop: 2 }}>{n.regDt}</div>
          </div>
        ))
      )}
    </div>
  )

  // 현재 경로의 메뉴명을 문서 제목에 반영(없으면 사이트명만)
  useDocumentTitle(labels.get(location.pathname), siteTitle)

  const logout = async () => {
    await authLogout() // 서버 token_ver 증가(토큰 무효화) + 로컬 정리
    navigate('/login', { replace: true })
  }

  const onMenuClick: MenuProps['onClick'] = (e) => {
    if (e.key.startsWith('/')) navigate(e.key)
    setDrawerOpen(false)
  }

  // 로그인 회원 전용 '나의 취미' 탭을 nav에 주입(마이페이지 앞). menu 변경 없이 코드로 노출(헤더 마이페이지 버튼과 동일 방식).
  const myHobbyNode: NavNode = { key: 'myhobby', label: '나의 취미', iconKey: 'star', dest: '/gen/myhobby' }
  const displayNav: NavNode[] = loggedIn
    ? (() => {
        const idx = nav.findIndex((n) => n.dest === '/gen/mypage')
        return idx < 0 ? [...nav, myHobbyNode] : [...nav.slice(0, idx), myHobbyNode, ...nav.slice(idx)]
      })()
    : nav
  const displayItems: MenuItem[] = loggedIn ? [...items, { key: '/gen/myhobby', label: '나의 취미' }] : items

  /*
    ★ 모든 화면의 최상단이 어두운 비주얼(메인=히어로, 하위=헤드 밴드)이므로 헤더는 그 위에
    투명하게 떠 있고, 스크롤하면 클래스가 떨어져 index.css의 유리면 헤더로 돌아온다.
    하위 페이지만 흰 헤더로 두면 메인과 톤이 갈려 사이트가 두 개처럼 보인다.
  */
  const headerClass = solid ? 'gen-header' : 'gen-header is-hero'
  const footerText = footerTemplate
    .replaceAll('{year}', String(new Date().getFullYear()))
    .replaceAll('{title}', siteTitle)

  return (
    <ConfigProvider theme={genTheme}>
      <Layout style={{ minHeight: '100vh', backgroundColor: gen.pageBg, backgroundImage: `url(${hobbyPattern})`, backgroundAttachment: 'fixed' }}>
        {/* 배경은 .gen-header(반투명+blur)가 담당 — 인라인 background를 주면 불투명해져 유리면이 사라진다 */}
        {/*
          ★ 그리드 열을 `auto minmax(0,1fr) auto`로 둔다. 예전엔 `1fr auto 1fr`로 중앙 열을
          콘텐츠 폭에 맞춰 "뷰포트 정중앙"에 두려 했는데, 로그인 시 항목이 늘면(내 피드·모집·공지·
          고객센터·나의 취미 + 검색 + 버튼 4개) 1280px에서도 중앙 내비가 우측 컨트롤과
          **159px 겹쳤다**(실측). 이제 좌·우가 자기 폭을 갖고 내비는 남은 공간 안에서 가운데 정렬된다.
        */}
        <Layout.Header className={headerClass} style={{ display: 'grid', gridTemplateColumns: 'auto minmax(0, 1fr) auto', alignItems: 'center', height: HEADER_H, paddingInline: 22, columnGap: 14, position: 'sticky', top: 0, zIndex: 20 }}>
          {/* 좌: 로고(모바일은 햄버거 포함) */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, justifySelf: 'start', minWidth: 0 }}>
            {isMobile && (
              <Button aria-label="메뉴 열기" onClick={() => setDrawerOpen(true)}>☰</Button>
            )}
            {/* 투명 헤더(히어로 위)에서는 로고 이미지 대신 사이트명 텍스트를 흰색으로 — CSS가 전환한다.
                업로드 로고는 흰 배경 기준이라 어두운 히어로 위에서 안 보인다. */}
            <img src={logoSrc} alt={siteTitle} style={{ height: 38, cursor: 'pointer' }} onClick={() => navigate('/gen')} />
            <span className="gen-logo-text" onClick={() => navigate('/gen')}>{siteTitle}</span>
          </div>

          {/* 중: 메뉴 — 남은 공간 안에서 가운데 정렬(넘치면 줄바꿈 없이 좁아진다). 데스크톱만 렌더 */}
          <nav className="gen-nav" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, minWidth: 0 }}>
            {!isMobile && displayNav.map((n) => {
              const active = n.dest
                ? location.pathname === n.dest
                : !!n.children?.some((c) => c.dest === location.pathname)
              const inner = (
                <>
                  <MenuGlyph name={n.iconKey} size={20} />
                  <span className="gen-nav-label">{n.label}</span>
                  <i className="gen-nav-bar" />
                </>
              )
              if (n.children?.length) {
                return (
                  <Dropdown
                    key={n.key}
                    menu={{
                      items: n.children.map((c) => ({
                        key: c.dest || c.key,
                        label: c.label,
                        onClick: () => c.dest && navigate(c.dest),
                      })),
                    }}
                  >
                    <button type="button" className={`gen-nav-btn${active ? ' is-active' : ''}`}>
                      {inner}
                    </button>
                  </Dropdown>
                )
              }
              return (
                <button
                  key={n.key}
                  type="button"
                  className={`gen-nav-btn${active ? ' is-active' : ''}`}
                  onClick={() => n.dest && navigate(n.dest)}
                >
                  {inner}
                </button>
              )
            })}
          </nav>

          {/* 우: 검색 + 컨트롤(알림/마이페이지/로그인 등) */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, justifySelf: 'end', minWidth: 0 }}>
            {!isMobile && (
              <Input.Search
                placeholder="검색" allowClear
                style={{ width: 150 }}
                onSearch={(v) => { const q = v.trim(); if (q) navigate(`/gen/search?q=${encodeURIComponent(q)}`) }}
              />
            )}
            <Space size={8}>
              {loggedIn ? (
                <>
                  <Popover content={notificationContent} trigger="click" open={notiOpen} onOpenChange={openNoti} placement="bottomRight">
                    <Badge count={notiUnread} size="small">
                      <Button shape="circle" aria-label="알림">🔔</Button>
                    </Badge>
                  </Popover>
                  <Badge count={msgUnread} size="small">
                    <Button shape="circle" aria-label="쪽지" onClick={() => navigate('/gen/message')}>✉️</Button>
                  </Badge>
                  <Button onClick={() => navigate('/gen/mypage')}>마이페이지</Button>
                  {isAdmin() && <Button onClick={() => navigate('/adm/dashboard')}>관리자 페이지</Button>}
                  <Button onClick={logout}>로그아웃</Button>
                </>
              ) : (
                <Button type="primary" onClick={() => navigate('/login')} style={{ borderRadius: 14, fontWeight: 700 }}>로그인</Button>
              )}
            </Space>
          </div>
        </Layout.Header>

        {/* 모바일 메뉴 서랍 */}
        <Drawer
          title={siteTitle}
          placement="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          styles={{ body: { padding: 0 } }}
        >
          <Menu mode="inline" selectedKeys={[location.pathname]} items={displayItems} onClick={onMenuClick} />
        </Drawer>

        {/*
          ★ 모든 화면을 헤더 높이만큼 끌어올린다 — 헤더가 최상단 비주얼(히어로·헤드 밴드) 위에
          떠 있어야 하는데, sticky 헤더는 레이아웃 공간을 차지하므로 안 끌어올리면 비주얼 위에
          흰 띠가 남는다. 그만큼의 여백은 밴드가 padding-top으로, 헤드 없는 화면은
          `.gen-page:first-child`가 책임진다(gen.css).
        */}
        <Layout.Content style={{ margin: 0, marginTop: -HEADER_H }}>
          <CrumbProvider value={crumbs}>
          <Routes>
            <Route index element={<GenMain />} />
            <Route path="page/:pageId" element={<GenPageView />} />
            {genScreens.map((s) => (
              <Route key={s.path} path={s.path.replace(/^\/gen\/?/, '')} element={s.element} />
            ))}
            {/* 없는 주소 안내도 셸 안에 둔다 — Content 여백이 0이라 그냥 두면 헤더에 붙는다 */}
            <Route path="*" element={<PageBody narrow><NotFound /></PageBody>} />
          </Routes>
          </CrumbProvider>
        </Layout.Content>

        <Layout.Footer style={{ background: gen.headerBg, borderTop: '1px solid rgba(108,78,227,.12)', padding: '30px 24px 24px' }}>
          {/* 푸터도 본문과 같은 폭 컨테이너를 쓴다 — 폭이 다르면 줄이 어긋나 보인다 */}
          <div className="gen-wfix" style={{ display: 'flex', flexWrap: 'wrap', gap: 20, alignItems: 'flex-start', justifyContent: 'space-between' }}>
            <div>
              <img src={logoSrc} alt={siteTitle} style={{ height: 28, cursor: 'pointer' }} onClick={() => navigate('/gen')} />
              <div style={{ fontSize: 13, color: '#8078A8', marginTop: 10 }}>취미로 만나는 사람들 — 함께할 사람을 찾아보세요 💜</div>
            </div>
            <nav style={{ display: 'flex', flexWrap: 'wrap', gap: 18 }}>
              {nav
                .map((n) => ({ label: n.label, dest: n.dest || n.children?.find((c) => c.dest)?.dest }))
                .filter((l) => l.dest)
                .map((l) => (
                  <span key={l.dest} className="gen-foot-link" onClick={() => navigate(l.dest!)}>
                    {l.label}
                  </span>
                ))}
            </nav>
          </div>
          <div className="gen-wfix" style={{ marginTop: 20, paddingTop: 16, borderTop: '1px dashed rgba(108,78,227,.18)', display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'space-between', fontSize: 12.5, color: '#9A93B8' }}>
            {/* 저작권 줄은 확장설정(site.footer-text)에서 온다. 비우면 줄을 감춘다 */}
            {footerText.trim() && <span style={{ whiteSpace: 'pre-line' }}>{footerText}</span>}
            {/* 약관·개인정보처리방침은 상시 열람할 수 있어야 한다(가입 동의 항목과 같은 문서를 그대로 노출) */}
            <Space size={12} wrap>
              {policies.map((p) => (
                <span key={p.rowId} className="gen-foot-link" onClick={() => setViewPolicyId(p.rowId)}>
                  {p.title}
                </span>
              ))}
            </Space>
            <span>Made with 💜 for hobby lovers</span>
          </div>
        </Layout.Footer>
      </Layout>

      <button
        type="button"
        className={`gen-top-btn${showTop ? ' is-show' : ''}`}
        aria-label="맨 위로"
        onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
      >
        ↑
      </button>

      <Modal
        open={warningOpen}
        title="자동 로그아웃 안내"
        closable={false}
        maskClosable={false}
        okText="계속 이용"
        cancelText="로그아웃"
        onOk={extend}
        onCancel={logoutNow}
      >
        <p>
          장시간 활동이 없어 <b>{remainingSec}초</b> 후 자동 로그아웃됩니다.
        </p>
        <p>계속 이용하시려면 [계속 이용]을 눌러주세요.</p>
      </Modal>
      <PolicyViewModal rowId={viewPolicyId} onClose={() => setViewPolicyId(undefined)} />
    </ConfigProvider>
  )
}
