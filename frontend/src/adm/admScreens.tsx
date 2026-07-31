import type { ComponentType } from 'react'

/**
 * 관리자 화면 = 규칙 기반 자동 매핑 (하드코딩 레지스트리 제거).
 *   메뉴 link_url = /adm/{domain}   ↔   파일 src/adm/{domain}/{X}Page.tsx (폴더당 1개)
 * → 메뉴명·노출·순서·계층은 전부 t_menu(DB)가 단일 소스, 컴포넌트는 파일 규칙으로 자동 연결.
 *   새 기능화면: 규칙에 맞는 폴더/파일만 만들면 등록 코드 없이 동작. (컴포넌트 자체는 코드라 파일은 필요)
 *
 * import.meta.glob: 빌드시 src/adm/[각 도메인]/*Page.tsx 를 정적 수집(eager). Modal/api 등은 미포함.
 */
const modules = import.meta.glob('./*/*Page.tsx', { eager: true }) as Record<string, { default: ComponentType }>

const screenByDomain: Record<string, ComponentType> = {}
for (const [file, mod] of Object.entries(modules)) {
  const m = file.match(/^\.\/([^/]+)\/[^/]+Page\.tsx$/) // './code/CodeListPage.tsx' → 'code'
  if (m && mod?.default) screenByDomain[m[1]] = mod.default
}

/** link_url(/adm/{domain}) → 화면 컴포넌트. 매칭 컴포넌트 없으면 undefined */
export function resolveScreen(path: string): ComponentType | undefined {
  const domain = path.split('/')[2] // '/adm/code' → 'code'
  return domain ? screenByDomain[domain] : undefined
}

export const DEFAULT_PATH = '/adm/dashboard'
