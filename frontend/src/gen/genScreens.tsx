import type { ReactNode } from 'react'
import GenMain from './GenMain'
import GenBoard from './GenBoard'
import RecruitPage from './recruit/RecruitPage'
import GenHobby from './hobby/GenHobby'

/**
 * 사용자(gen) 화면 레지스트리 — adm/admScreens.tsx와 동일 패턴.
 * gen 화면을 만들면 여기에 { path, label, element } 로 등록.
 */
export interface GenScreen {
  path: string
  label: string
  element: ReactNode
}

export const genScreens: GenScreen[] = [
  { path: '/gen/main', label: '메인', element: <GenMain /> },
  { path: '/gen/board/:bbsinfoId', label: '게시판', element: <GenBoard /> },
  { path: '/gen/recruit', label: '모집', element: <RecruitPage /> },
  { path: '/gen/hobby/:id', label: '취미', element: <GenHobby /> },
]

export const GEN_DEFAULT_PATH = '/gen'
