import { useEffect, useMemo, useRef, useState } from 'react'
import { Button, ConfigProvider, Drawer, Dropdown, Grid, Input, Layout, Menu, Modal, Space, Tabs, Tooltip } from 'antd'
import type { MenuProps } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import { extendPw, logout as authLogout } from '../api/auth'
import { admTheme } from '../adm/theme'
import { menuApi } from '../adm/menu/menu.api'
import type { Menu as MenuVO } from '../adm/menu/menu.api'
import { configApi } from '../adm/config/config.api'
import { resolveScreen, DEFAULT_PATH } from '../adm/admScreens'
import { useIdleLogout } from '../common/hooks/useIdleLogout'
import { useDocumentTitle } from '../common/hooks/useDocumentTitle'
import { useLocalState } from '../common/hooks/useLocalState'
import { useRecentMenus } from '../common/hooks/useRecentMenus'
import PasswordChangeModal from '../common/adm/components/PasswordChangeModal'
import MenuGlyph from '../common/adm/components/MenuGlyph'
import VersionBadge from '../common/adm/components/VersionBadge'
import defaultLogo from '../assets/logo.svg'

type MenuItem = Required<MenuProps>['items'][number]

/**
 * ADM 메뉴 목적지 경로. 게시판(MENU02)→/adm/post/{connId}(게시글 관리화면), URL(MENU01)→link_url.
 * GenLayout과 동일하게 conn_cd로 목적지를 만든다(게시판은 피커로 연결, URL 수기입력 불필요).
 */
function menuDest(m: MenuVO): string | null {
  if (m.connCd === 'MENU02') return m.connId ? `/adm/post/${m.connId}` : null
  if (m.connCd === 'MENU01') return m.linkUrl || null
  return null // MENU03(페이지)·MENU04(그룹) 등은 ADM에서 직접 경로 없음
}

/** 플랫 메뉴 목록(menu)을 AntD 계층 메뉴로 변환. 자식 있으면 SubMenu, 아니면 링크(key=목적지 경로).
 *  아이콘: 메뉴에 icon 지정 시 그 아이콘, 미지정이면 최상위만 기본(grid) 표시. 글자와 간격(6px) 확보. */
function buildItems(list: MenuVO[]): MenuItem[] {
  const byParent = new Map<string, MenuVO[]>()
  for (const m of list) {
    const p = m.pMenuId ?? '0'
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p)!.push(m)
  }
  const build = (parentId: string, depth: number): MenuItem[] =>
    (byParent.get(parentId) ?? []).map((m) => {
      const icon =
        depth === 0 || m.icon ? (
          <span style={{ display: 'inline-flex', marginInlineEnd: 6 }}>
            <MenuGlyph name={m.icon} size={18} />
          </span>
        ) : undefined
      const children = byParent.get(m.rowId!)
      if (children && children.length) {
        return { key: `g${m.rowId}`, label: m.menuName, icon, children: build(m.rowId!, depth + 1) }
      }
      return { key: menuDest(m) || `m${m.rowId}`, label: m.menuName, icon }
    })
  return build('0', 0)
}

/**
 * 관리자 공통 레이아웃 — 좌측 동적 메뉴(menu) + 상단 탭.
 * 메뉴 클릭 시 탭으로 열리고, 탭 전환 시 화면 상태 유지(AntD Tabs keep-alive).
 */
export default function AdmLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const path = location.pathname
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg // lg 미만이면 사이드바 대신 서랍

  const [menuList, setMenuList] = useState<MenuVO[]>([]) // 원본 목록 — 검색 필터의 기준
  const [menuKeyword, setMenuKeyword] = useState('')
  const [menuNames, setMenuNames] = useState<Record<string, string>>({}) // link_url → menu_name (탭 제목 단일 소스)
  const [openKeys, setOpenKeys] = useState<string[]>([])
  const [openPaths, setOpenPaths] = useState<string[]>([DEFAULT_PATH])
  const [pwModalOpen, setPwModalOpen] = useState(false)
  const [idleMinutes, setIdleMinutes] = useState(0)
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo

  // 화면 편의 설정 — 이 브라우저에만 남는 값(서버 저장 아님)
  const [collapsed, setCollapsed] = useLocalState('adm.sidebarCollapsed', false)
  const [fontLevel, setFontLevel] = useLocalState('adm.fontLevel', 1) // 0.9 / 1 / 1.1 / 1.2
  const { list: recentMenus, record: recordRecent } = useRecentMenus()

  // 글자크기는 AntD 기준 폰트(14px)와 메뉴 폰트를 같은 배율로 키운다 → 화면 전체가 일관되게 커진다
  const scaledTheme = useMemo(
    () => ({
      ...admTheme,
      token: { ...admTheme.token, fontSize: Math.round(14 * fontLevel) },
      components: {
        ...admTheme.components,
        Menu: { ...admTheme.components?.Menu, fontSize: Math.round(15 * fontLevel) },
      },
    }),
    [fontLevel],
  )

  // 유휴 자동 로그아웃(config.session_expire_mins 분, 미설정 시 30분) + 1분 전 경고
  const { warningOpen, remainingSec, extend, logoutNow } = useIdleLogout(idleMinutes)

  // 좌측 메뉴 트리 로드 + 유휴시간 로드
  useEffect(() => {
    menuApi
      .tree('ADM')
      .then((list) => {
        setMenuList(list)
        // 탭 제목용 목적지경로→menu_name 매핑 (메뉴명 수정/추가가 탭에 자동 반영)
        setMenuNames(
          Object.fromEntries(
            list.map((m) => [menuDest(m), m.menuName] as const).filter((e): e is [string, string] => !!e[0]),
          ),
        )
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
  }, [])

  /**
   * 사이드바 메뉴 검색 — 메뉴가 늘어나면 트리를 펼쳐가며 찾는 게 번거로워진다.
   * 매칭된 메뉴 + 그 조상(부모 그룹)만 남긴다. 조상을 함께 남기지 않으면 트리 구조가 끊겨
   * 매칭된 하위 메뉴가 화면에서 사라진다.
   */
  const filteredMenu = useMemo(() => {
    const kw = menuKeyword.trim().toLowerCase()
    if (!kw) return menuList
    const byId = new Map(menuList.map((m) => [m.rowId!, m]))
    const keep = new Set<string>()
    for (const m of menuList) {
      if (!(m.menuName ?? '').toLowerCase().includes(kw)) continue
      keep.add(m.rowId!)
      let p = m.pMenuId
      while (p && p !== '0' && byId.has(p) && !keep.has(p)) {
        keep.add(p)
        p = byId.get(p)!.pMenuId
      }
    }
    return menuList.filter((m) => keep.has(m.rowId!))
  }, [menuList, menuKeyword])

  const items = useMemo(() => buildItems(filteredMenu), [filteredMenu])

  // 검색 중에는 결과의 부모 그룹을 자동으로 펼친다(접혀 있으면 매칭 결과가 안 보인다).
  const searchOpenKeys = useMemo(() => {
    if (!menuKeyword.trim()) return null
    const parentIds = new Set(filteredMenu.map((m) => m.pMenuId).filter((p): p is string => !!p && p !== '0'))
    return filteredMenu.filter((m) => parentIds.has(m.rowId!)).map((m) => `g${m.rowId}`)
  }, [filteredMenu, menuKeyword])

  // 현재 탭(메뉴명)을 문서 제목에 반영: "메뉴명 | 사이트명"
  useDocumentTitle(menuNames[path], siteTitle)

  // 로그인 시 비밀번호 만료면 안내(강제 아님): 지금 변경 / 나중에(연장). 1회만 표시.
  useEffect(() => {
    if (sessionStorage.getItem('pwExpired') !== 'Y') return
    sessionStorage.removeItem('pwExpired')
    Modal.confirm({
      title: '비밀번호 변경 안내',
      content: '비밀번호를 변경한 지 오래되었습니다. 지금 변경하시겠어요?',
      okText: '지금 변경',
      cancelText: '나중에',
      onOk: () => setPwModalOpen(true),
      onCancel: () => extendPw().catch(() => {}),
    })
  }, [])

  // 현재 경로를 탭으로 반영 (미등록 경로는 대시보드로)
  useEffect(() => {
    if (!resolveScreen(path)) {
      navigate(DEFAULT_PATH, { replace: true })
      return
    }
    setOpenPaths((prev) => (prev.includes(path) ? prev : [...prev, path]))
  }, [path, navigate])

  // 최근 사용 메뉴 기록 — menuNames가 채워진 뒤여야 경로 대신 메뉴명이 남는다.
  // 대시보드(기본 탭)는 항상 열려 있어 기록해도 의미가 없어 제외한다.
  useEffect(() => {
    const label = menuNames[path]
    if (label && path !== DEFAULT_PATH) recordRecent({ path, label })
  }, [path, menuNames, recordRecent])

  // 우클릭 메뉴 동작은 탭 활성화가 끝난 뒤(다음 tick) 실행되므로, 그때의 현재 경로를 봐야 한다.
  const pathRef = useRef(path)
  pathRef.current = path

  const closeTab = (target: string) => {
    setOpenPaths((prev) => {
      const remaining = prev.filter((p) => p !== target)
      if (pathRef.current === target) {
        navigate(remaining[remaining.length - 1] ?? DEFAULT_PATH)
      }
      return remaining
    })
  }

  /** 지정 탭만 남기고 닫기. 대시보드(기본 탭)는 닫지 않는다 — 항상 돌아갈 자리가 필요하다. */
  const closeOtherTabs = (keep: string) => {
    setOpenPaths((prev) => {
      const remaining = prev.filter((p) => p === keep || p === DEFAULT_PATH)
      if (!remaining.includes(pathRef.current)) navigate(keep)
      return remaining
    })
  }

  /** 전체 닫기 = 대시보드만 남긴다. */
  const closeAllTabs = () => {
    setOpenPaths([DEFAULT_PATH])
    navigate(DEFAULT_PATH)
  }

  const logout = async () => {
    await authLogout() // 서버 token_ver 증가(토큰 무효화) + 로컬 정리
    navigate('/login', { replace: true })
  }

  const tabItems = openPaths.map((p) => {
    const Screen = resolveScreen(p) // link_url → 컴포넌트(파일 규칙 자동 매핑)
    // 탭 제목은 menu(menu_name) 기준. 메뉴 없는 경로(게시판 설정→글 관리로 직접 진입하는
    // 취미 게시판 등)는 경로가 그대로 노출되지 않도록 화면명으로 대체한다.
    const title = menuNames[p] ?? (p.startsWith('/adm/post/') ? '게시글 관리' : p)
    return {
      key: p,
      // 우클릭 메뉴 — 탭을 여러 개 열어두고 작업할 때 하나씩 닫는 수고를 줄인다.
      label: (
        <Dropdown
          trigger={['contextMenu']}
          getPopupContainer={() => document.body}
          menu={{
            items: [
              { key: 'self', label: '이 탭 닫기', disabled: p === DEFAULT_PATH },
              { key: 'others', label: '다른 탭 닫기', disabled: openPaths.length <= 1 },
              { key: 'all', label: '전체 닫기', disabled: openPaths.length <= 1 },
            ],
            // React 포털은 DOM이 아니라 React 트리로 이벤트를 올려, 항목 클릭이 탭 활성화까지 발동한다.
            // stopPropagation으로 막으면 드롭다운이 닫히지 않으므로 활성화 후 다음 tick에 닫는다.
            onClick: ({ key }) => {
              setTimeout(() => {
                if (key === 'self') closeTab(p)
                else if (key === 'others') closeOtherTabs(p)
                else closeAllTabs()
              }, 0)
            },
          }}
        >
          <span>{title}</span>
        </Dropdown>
      ),
      closable: p !== DEFAULT_PATH, // 대시보드는 닫기 불가
      children: Screen ? <Screen /> : null,
    }
  })

  const menuNode = (
    <>
      {/* 메뉴 검색 — 사이드바를 접었을 땐 입력칸이 들어갈 자리가 없어 숨긴다 */}
      {!collapsed && (
        <div style={{ padding: '10px 12px 4px' }}>
          <Input
            allowClear
            placeholder="메뉴 검색"
            value={menuKeyword}
            onChange={(e) => setMenuKeyword(e.target.value)}
          />
        </div>
      )}
    <Menu
      mode="inline"
      selectedKeys={[path]}
      // 검색 중에는 결과의 부모를 강제로 펼치고, 검색을 지우면 사용자가 펼쳐둔 상태로 돌아간다
      openKeys={searchOpenKeys ?? openKeys}
      onOpenChange={(keys) => setOpenKeys(keys as string[])}
      items={items}
      onClick={(e) => {
        if (e.key.startsWith('/')) navigate(e.key)
        setDrawerOpen(false)
      }}
    />
      {!collapsed && menuKeyword.trim() && items.length === 0 && (
        <div style={{ padding: '8px 16px', color: '#999', fontSize: 13 }}>검색 결과가 없습니다.</div>
      )}
      <VersionBadge collapsed={collapsed} />
    </>
  )

  return (
    <ConfigProvider theme={scaledTheme}>
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Layout.Sider
          theme="light"
          width={260}
          // 접기 상태는 localStorage에 남아 새로고침·재로그인 후에도 유지된다
          collapsible
          collapsed={collapsed}
          onCollapse={(v) => setCollapsed(v)}
          collapsedWidth={64}
          style={{ borderRight: '1px solid #e8e8e8' }}
        >
          <div
            style={{
              height: 64, // 헤더(AntD Layout.Header 기본 64px)와 하단 구분선 높이 맞춤
              display: 'flex',
              alignItems: 'center',
              justifyContent: collapsed ? 'center' : 'flex-start',
              padding: collapsed ? 0 : '0 12px',
              borderBottom: '1px solid #e8e8e8',
              overflow: 'hidden',
            }}
          >
            {/* 고정 박스(236×48) + contain → 어떤 비율의 로고든 이 박스에 최대 크기로 들어감.
                접었을 땐 좁은 폭(48px)에 맞춰 축소 — 로고가 잘려 보이지 않게 한다. */}
            <img
              src={logoSrc}
              alt={siteTitle}
              style={{
                width: collapsed ? 48 : 236,
                height: collapsed ? 32 : 48,
                objectFit: 'contain',
                objectPosition: collapsed ? 'center' : 'left center',
                display: 'block',
              }}
            />
          </div>
          {menuNode}
        </Layout.Sider>
      )}
      <Layout>
        <Layout.Header
          style={{
            background: '#fff',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            paddingInline: 16,
            gap: 12,
            borderBottom: '1px solid #e8e8e8',
            boxShadow: '0 1px 4px rgba(0,0,0,.04)',
            position: 'sticky',
            top: 0,
            zIndex: 10,
          }}
        >
          {isMobile ? (
            <Button aria-label="메뉴 열기" onClick={() => setDrawerOpen(true)}>
              ☰
            </Button>
          ) : (
            <span />
          )}
          <Space>
            {/* 세션 잔여시간 — 갑자기 로그아웃되는 걸 막으려면 남은 시간이 보여야 한다. 클릭 시 연장. */}
            {idleMinutes > 0 && remainingSec > 0 && (
              <Tooltip title="클릭하면 세션이 연장됩니다">
                <Button
                  size="small"
                  danger={remainingSec <= 60}
                  onClick={extend}
                  style={{ fontVariantNumeric: 'tabular-nums' }} // 초가 바뀔 때 폭이 흔들리지 않게
                >
                  ⏱ {Math.floor(remainingSec / 60)}:{String(remainingSec % 60).padStart(2, '0')}
                </Button>
              </Tooltip>
            )}
            {/* 최근 사용 메뉴 — 자주 오가는 화면을 메뉴 트리에서 다시 찾지 않게 한다 */}
            <Dropdown
              trigger={['click']}
              menu={{
                items: recentMenus.length
                  ? recentMenus.map((m) => ({ key: m.path, label: m.label }))
                  : [{ key: 'empty', label: '최근 사용한 메뉴가 없습니다', disabled: true }],
                onClick: ({ key }) => {
                  if (key !== 'empty') navigate(key)
                },
              }}
            >
              <Button>최근 메뉴</Button>
            </Dropdown>
            {/* 글자크기 — 0.9~1.2배. AntD 기준 폰트를 바꿔 화면 전체가 같은 비율로 커진다 */}
            <Space.Compact>
              <Tooltip title="글자 작게">
                <Button
                  disabled={fontLevel <= 0.9}
                  onClick={() => setFontLevel(Math.round((fontLevel - 0.1) * 10) / 10)}
                >
                  가⁻
                </Button>
              </Tooltip>
              <Tooltip title="기본 크기로">
                <Button onClick={() => setFontLevel(1)}>{Math.round(fontLevel * 100)}%</Button>
              </Tooltip>
              <Tooltip title="글자 크게">
                <Button
                  disabled={fontLevel >= 1.2}
                  onClick={() => setFontLevel(Math.round((fontLevel + 0.1) * 10) / 10)}
                >
                  가⁺
                </Button>
              </Tooltip>
            </Space.Compact>
            <Button onClick={() => navigate('/gen/main')}>홈페이지</Button>
            <Button onClick={() => setPwModalOpen(true)}>비밀번호 변경</Button>
            <Button onClick={logout}>로그아웃</Button>
          </Space>
        </Layout.Header>
        <Layout.Content style={{ margin: 16 }}>
          <Tabs
            type="editable-card"
            hideAdd
            tabBarStyle={{ marginBottom: 0 }} // 탭 바와 본문 사이 기본 여백(16px) 제거 → 경계선 붙임
            activeKey={path}
            onChange={(k) => navigate(k)}
            onEdit={(key, action) => {
              if (action === 'remove') closeTab(key as string)
            }}
            items={tabItems}
          />
        </Layout.Content>
      </Layout>
    </Layout>
    <Drawer
      title={<img src={logoSrc} alt={siteTitle} style={{ width: 220, height: 40, objectFit: 'contain', objectPosition: 'left center' }} />}
      placement="left"
      open={drawerOpen}
      onClose={() => setDrawerOpen(false)}
      styles={{ body: { padding: 0 } }}
    >
      {menuNode}
    </Drawer>
    <PasswordChangeModal open={pwModalOpen} onClose={() => setPwModalOpen(false)} />
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
      <p>장시간 활동이 없어 <b>{remainingSec}초</b> 후 자동 로그아웃됩니다.</p>
      <p>계속 이용하시려면 [계속 이용]을 눌러주세요.</p>
    </Modal>
    </ConfigProvider>
  )
}
