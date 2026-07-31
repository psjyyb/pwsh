import { useEffect, useState } from 'react'
import { Button, ConfigProvider, Drawer, Grid, Layout, Menu, Modal, Space } from 'antd'
import type { MenuProps } from 'antd'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { genTheme } from './theme'
import { tokenStore, isAdmin } from '../auth/token'
import { logout as authLogout } from '../api/auth'
import { configApi } from '../adm/config/config.api'
import { menuApi } from '../adm/menu/menu.api'
import type { Menu as MenuVO } from '../adm/menu/menu.api'
import { useIdleLogout } from '../common/hooks/useIdleLogout'
import { useDocumentTitle } from '../common/hooks/useDocumentTitle'
import { genScreens } from './genScreens'
import GenPageView from './GenPageView'
import NotFound from '../common/NotFound'

type MenuItem = Required<MenuProps>['items'][number]

/** conn_ty별 이동 경로. 페이지→/gen/page/{connId}, URL→link_url, 게시판→/gen/board/{connId}, 그룹→없음 */
function targetOf(m: MenuVO): string | null {
  if (m.connTy === 'MENU03') return m.connId ? `/gen/page/${m.connId}` : null
  if (m.connTy === 'MENU02') return m.connId ? `/gen/board/${m.connId}` : null
  if (m.connTy === 'MENU01') return m.linkUrl || null
  return null
}

/** 플랫 메뉴(t_menu GEN) → AntD 계층 메뉴. 자식 있으면 하위메뉴, 아니면 conn_ty 목적지를 key로 */
function buildItems(list: MenuVO[]): MenuItem[] {
  const byParent = new Map<string, MenuVO[]>()
  for (const m of list) {
    const p = m.pMenuId ?? '0'
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p)!.push(m)
  }
  const build = (parentId: string): MenuItem[] =>
    (byParent.get(parentId) ?? []).map((m) => {
      const children = byParent.get(m.dbKey!)
      if (children && children.length) {
        return { key: `g${m.dbKey}`, label: m.menuNm, children: build(m.dbKey!) }
      }
      return { key: targetOf(m) || `m${m.dbKey}`, label: m.menuNm }
    })
  return build('0')
}

/** 경로 → 메뉴명 맵(문서 제목/현재 메뉴 표시용) */
function labelMap(list: MenuVO[]): Map<string, string> {
  const map = new Map<string, string>()
  list.forEach((m) => {
    const t = targetOf(m)
    if (t) map.set(t, m.menuNm ?? '')
  })
  return map
}

/**
 * 사용자(gen) 공통 레이아웃 — 동적 메뉴(t_menu area=GEN) + conn_ty 기반 라우팅.
 * 페이지관리에서 만든 콘텐츠를 메뉴(연결유형=페이지)로 연결하면 코드 수정 없이 노출됨(GenPageView).
 * 데스크톱=수평 메뉴, 모바일(md 미만)=햄버거+Drawer. 사이트명/문서제목은 환경설정(t_config.title) 연동.
 */
export default function GenLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.md
  const [items, setItems] = useState<MenuItem[]>([])
  const [labels, setLabels] = useState<Map<string, string>>(new Map())
  const [siteTitle, setSiteTitle] = useState('PWSH')
  const [idleMinutes, setIdleMinutes] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const loggedIn = !!tokenStore.get() // 비로그인(게스트)도 /gen 접근 가능 — 메뉴는 GUEST 권한그룹 기준
  const { warningOpen, remainingSec, extend, logoutNow } = useIdleLogout(loggedIn ? idleMinutes : 0)

  useEffect(() => {
    menuApi
      .tree('GEN')
      .then((list) => {
        setItems(buildItems(list))
        setLabels(labelMap(list))
      })
      .catch(() => {
        /* 메뉴 미시드 시 빈 메뉴 */
      })
    configApi
      .view()
      .then((c) => {
        setIdleMinutes(Number(c.sessionExpireCnt) || 30)
        if (c.title) setSiteTitle(c.title)
      })
      .catch(() => setIdleMinutes(30))
  }, [])

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

  return (
    <ConfigProvider theme={genTheme}>
      <Layout style={{ minHeight: '100vh' }}>
        <Layout.Header style={{ background: '#fff', display: 'flex', alignItems: 'center', paddingInline: 16, gap: 16 }}>
          {isMobile && (
            <Button aria-label="메뉴 열기" onClick={() => setDrawerOpen(true)}>
              ☰
            </Button>
          )}
          <span style={{ fontWeight: 700, whiteSpace: 'nowrap', cursor: 'pointer' }} onClick={() => navigate('/gen')}>
            {siteTitle}
          </span>
          {!isMobile && (
            <Menu
              mode="horizontal"
              selectedKeys={[location.pathname]}
              items={items}
              style={{ flex: 1, minWidth: 0 }}
              onClick={onMenuClick}
            />
          )}
          <Space style={{ marginLeft: 'auto' }}>
            {loggedIn ? (
              <>
                {isAdmin() && <Button onClick={() => navigate('/adm/dashboard')}>관리자 페이지</Button>}
                <Button onClick={logout}>로그아웃</Button>
              </>
            ) : (
              <Button type="primary" onClick={() => navigate('/login')}>로그인</Button>
            )}
          </Space>
        </Layout.Header>

        {/* 모바일 메뉴 서랍 */}
        <Drawer
          title={siteTitle}
          placement="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          styles={{ body: { padding: 0 } }}
        >
          <Menu mode="inline" selectedKeys={[location.pathname]} items={items} onClick={onMenuClick} />
        </Drawer>

        <Layout.Content style={{ margin: 16 }}>
          <Routes>
            <Route index element={<div>환영합니다. 상단 메뉴에서 원하는 항목을 선택하세요.</div>} />
            <Route path="page/:pageId" element={<GenPageView />} />
            {genScreens.map((s) => (
              <Route key={s.path} path={s.path.replace(/^\/gen\/?/, '')} element={s.element} />
            ))}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Layout.Content>

        <Layout.Footer style={{ textAlign: 'center', color: '#888' }}>
          © {new Date().getFullYear()} {siteTitle}. All rights reserved.
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
