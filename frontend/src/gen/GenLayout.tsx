import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, ConfigProvider, Dropdown, Drawer, Grid, Input, Layout, Menu, Modal, Popover, Space } from 'antd'
import type { MenuProps } from 'antd'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { genTheme, gen } from './theme'
import { tokenStore, isAdmin } from '../auth/token'
import { logout as authLogout } from '../api/auth'
import { configApi } from '../adm/config/config.api'
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
import { useEventStream } from '../common/gen/useEventStream'
import { notificationApi } from '../api/notification'
import { messageApi } from '../api/message'
import type { Noti } from '../api/notification'

type MenuItem = Required<MenuProps>['items'][number]

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
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo
  const [idleMinutes, setIdleMinutes] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [notiUnread, setNotiUnread] = useState(0)
  const [msgUnread, setMsgUnread] = useState(0)
  const [notiList, setNotiList] = useState<Noti[]>([])
  const [notiOpen, setNotiOpen] = useState(false)
  const loggedIn = !!tokenStore.get() // 비로그인(게스트)도 /gen 접근 가능 — 메뉴는 GUEST 권한그룹 기준
  const { warningOpen, remainingSec, extend, logoutNow } = useIdleLogout(loggedIn ? idleMinutes : 0)

  useEffect(() => {
    menuApi
      .tree('GEN')
      .then((list) => {
        setItems(buildItems(list))
        setNav(toNav(list))
        setLabels(labelMap(list))
      })
      .catch(() => {
        /* 메뉴 미시드 시 빈 메뉴 */
      })
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

  return (
    <ConfigProvider theme={genTheme}>
      <Layout style={{ minHeight: '100vh', backgroundColor: gen.pageBg, backgroundImage: `url(${hobbyPattern})`, backgroundAttachment: 'fixed' }}>
        {/* 배경은 .gen-header(반투명+blur)가 담당 — 인라인 background를 주면 불투명해져 유리면이 사라진다 */}
        <Layout.Header className="gen-header" style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', alignItems: 'center', height: 72, paddingInline: 22, columnGap: 18, position: 'sticky', top: 0, zIndex: 20 }}>
          {/* 좌: 로고(모바일은 햄버거 포함) */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, justifySelf: 'start', minWidth: 0 }}>
            {isMobile && (
              <Button aria-label="메뉴 열기" onClick={() => setDrawerOpen(true)}>☰</Button>
            )}
            <img src={logoSrc} alt={siteTitle} style={{ height: 38, cursor: 'pointer' }} onClick={() => navigate('/gen')} />
          </div>

          {/* 중: 메뉴 — 그리드 가운데 열(auto)이라 좌우 요소 폭과 무관하게 항상 뷰포트 정중앙. 데스크톱만 렌더 */}
          <nav className="gen-nav" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 14 }}>
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
                placeholder="취미·모집·글 검색" allowClear
                style={{ width: 190 }}
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

        <Layout.Content style={{ margin: 16 }}>
          <Routes>
            <Route index element={<GenMain />} />
            <Route path="page/:pageId" element={<GenPageView />} />
            {genScreens.map((s) => (
              <Route key={s.path} path={s.path.replace(/^\/gen\/?/, '')} element={s.element} />
            ))}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Layout.Content>

        <Layout.Footer style={{ background: gen.headerBg, borderTop: '1px solid rgba(108,78,227,.12)', padding: '30px 24px 24px' }}>
          <div style={{ maxWidth: 1080, margin: '0 auto', display: 'flex', flexWrap: 'wrap', gap: 20, alignItems: 'flex-start', justifyContent: 'space-between' }}>
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
          <div style={{ maxWidth: 1080, margin: '20px auto 0', paddingTop: 16, borderTop: '1px dashed rgba(108,78,227,.18)', display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'space-between', fontSize: 12.5, color: '#9A93B8' }}>
            <span>© {new Date().getFullYear()} {siteTitle}. All rights reserved.</span>
            <span>Made with 💜 for hobby lovers</span>
          </div>
        </Layout.Footer>
      </Layout>
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
    </ConfigProvider>
  )
}
