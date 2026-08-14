import { useEffect, useState } from 'react'
import { Button, ConfigProvider, Drawer, Grid, Layout, Menu, Modal, Space, Tabs } from 'antd'
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
import PasswordChangeModal from '../common/adm/components/PasswordChangeModal'
import MenuGlyph from '../common/adm/components/MenuGlyph'
import defaultLogo from '../assets/logo.svg'

type MenuItem = Required<MenuProps>['items'][number]

/**
 * ADM 메뉴 목적지 경로. 게시판(MENU02)→/adm/bbs/{connId}(게시글 관리화면), URL(MENU01)→link_url.
 * GenLayout과 동일하게 conn_ty로 목적지를 만든다(게시판은 피커로 연결, URL 수기입력 불필요).
 */
function menuDest(m: MenuVO): string | null {
  if (m.connTy === 'MENU02') return m.connId ? `/adm/bbs/${m.connId}` : null
  if (m.connTy === 'MENU01') return m.linkUrl || null
  return null // MENU03(페이지)·MENU04(그룹) 등은 ADM에서 직접 경로 없음
}

/** 플랫 메뉴 목록(t_menu)을 AntD 계층 메뉴로 변환. 자식 있으면 SubMenu, 아니면 링크(key=목적지 경로).
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
        return { key: `g${m.rowId}`, label: m.menuNm, icon, children: build(m.rowId!, depth + 1) }
      }
      return { key: menuDest(m) || `m${m.rowId}`, label: m.menuNm, icon }
    })
  return build('0', 0)
}

/**
 * 관리자 공통 레이아웃 — 좌측 동적 메뉴(t_menu) + 상단 탭.
 * 메뉴 클릭 시 탭으로 열리고, 탭 전환 시 화면 상태 유지(AntD Tabs keep-alive).
 */
export default function AdmLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const path = location.pathname
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg // lg 미만이면 사이드바 대신 서랍

  const [items, setItems] = useState<MenuItem[]>([])
  const [menuNames, setMenuNames] = useState<Record<string, string>>({}) // link_url → menu_nm (탭 제목 단일 소스)
  const [openKeys, setOpenKeys] = useState<string[]>([])
  const [openPaths, setOpenPaths] = useState<string[]>([DEFAULT_PATH])
  const [pwModalOpen, setPwModalOpen] = useState(false)
  const [idleMinutes, setIdleMinutes] = useState(0)
  const [siteTitle, setSiteTitle] = useState('취만사')
  const [logoFileId, setLogoFileId] = useState<string | undefined>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const logoSrc = logoFileId ? `/api/pub/image/${logoFileId}` : defaultLogo

  // 유휴 자동 로그아웃(t_config.session_expire_cnt 분, 미설정 시 30분) + 1분 전 경고
  const { warningOpen, remainingSec, extend, logoutNow } = useIdleLogout(idleMinutes)

  // 좌측 메뉴 트리 로드 + 유휴시간 로드
  useEffect(() => {
    menuApi
      .tree('ADM')
      .then((list) => {
        setItems(buildItems(list))
        // 탭 제목용 목적지경로→menu_nm 매핑 (메뉴명 수정/추가가 탭에 자동 반영)
        setMenuNames(
          Object.fromEntries(
            list.map((m) => [menuDest(m), m.menuNm] as const).filter((e): e is [string, string] => !!e[0]),
          ),
        )
      })
      .catch(() => {
        /* 메뉴 미시드 시 빈 메뉴 */
      })
    configApi
      .view()
      .then((c) => {
        setIdleMinutes(Number(c.sessionExpireCnt) || 30)
        if (c.title) setSiteTitle(c.title)
        setLogoFileId(c.logoFileId ?? undefined)
      })
      .catch(() => setIdleMinutes(30))
  }, [])

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

  const closeTab = (target: string) => {
    const remaining = openPaths.filter((p) => p !== target)
    setOpenPaths(remaining)
    if (path === target) {
      navigate(remaining[remaining.length - 1] ?? DEFAULT_PATH)
    }
  }

  const logout = async () => {
    await authLogout() // 서버 token_ver 증가(토큰 무효화) + 로컬 정리
    navigate('/login', { replace: true })
  }

  const tabItems = openPaths.map((p) => {
    const Screen = resolveScreen(p) // link_url → 컴포넌트(파일 규칙 자동 매핑)
    return {
      key: p,
      label: menuNames[p] ?? p, // 탭 제목은 t_menu(menu_nm) 기준
      closable: p !== DEFAULT_PATH, // 대시보드는 닫기 불가
      children: Screen ? <Screen /> : null,
    }
  })

  const menuNode = (
    <Menu
      mode="inline"
      selectedKeys={[path]}
      openKeys={openKeys}
      onOpenChange={(keys) => setOpenKeys(keys as string[])}
      items={items}
      onClick={(e) => {
        if (e.key.startsWith('/')) navigate(e.key)
        setDrawerOpen(false)
      }}
    />
  )

  return (
    <ConfigProvider theme={admTheme}>
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Layout.Sider theme="light" width={260} style={{ borderRight: '1px solid #e8e8e8' }}>
          <div
            style={{
              height: 64, // 헤더(AntD Layout.Header 기본 64px)와 하단 구분선 높이 맞춤
              display: 'flex',
              alignItems: 'center',
              padding: '0 12px',
              borderBottom: '1px solid #e8e8e8',
            }}
          >
            {/* 고정 박스(236×48) + contain → 어떤 비율의 로고든 이 박스에 최대 크기로 들어감(일관된 footprint) */}
            <img
              src={logoSrc}
              alt={siteTitle}
              style={{ width: 236, height: 48, objectFit: 'contain', objectPosition: 'left center', display: 'block' }}
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
