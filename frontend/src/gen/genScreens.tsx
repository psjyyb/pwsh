import type { ReactNode } from 'react'
import GenMain from './GenMain'
import GenBoard from './GenBoard'
import RecruitPage from './recruit/RecruitPage'
import GenHobby from './hobby/GenHobby'
import MyPage from './mypage/MyPage'
import MyHobbyPage from './myhobby/MyHobbyPage'
import FeedPage from './feed/FeedPage'
import SearchPage from './search/SearchPage'
import MemberProfilePage from './profile/MemberProfilePage'
import MessagePage from './message/MessagePage'
import OnboardingPage from './onboarding/OnboardingPage'

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
  { path: '/gen/board/:boardId', label: '게시판', element: <GenBoard /> },
  { path: '/gen/recruit', label: '모집', element: <RecruitPage /> },
  { path: '/gen/recruit/:id', label: '모집', element: <RecruitPage /> },
  { path: '/gen/hobby/:id', label: '취미', element: <GenHobby /> },
  { path: '/gen/myhobby', label: '나의 취미', element: <MyHobbyPage /> },
  { path: '/gen/onboarding', label: '관심 취미 고르기', element: <OnboardingPage /> },
  { path: '/gen/feed', label: '내 피드', element: <FeedPage /> },
  { path: '/gen/search', label: '검색', element: <SearchPage /> },
  { path: '/gen/member/:memberId', label: '회원 프로필', element: <MemberProfilePage /> },
  { path: '/gen/message', label: '쪽지', element: <MessagePage /> },
  { path: '/gen/mypage', label: '마이페이지', element: <MyPage /> },
]

export const GEN_DEFAULT_PATH = '/gen'
