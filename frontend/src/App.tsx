import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './auth/LoginPage'
import SignupPage from './auth/SignupPage'
import ProtectedRoute from './auth/ProtectedRoute'
import AdmLayout from './layouts/AdmLayout'
import GenLayout from './gen/GenLayout'
import NotFound from './common/NotFound'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />

      {/* 관리자 영역 (/adm/*) — 로그인 + 관리자(MEM02)만. 화면은 AdmLayout 내부 탭에서 렌더 */}
      <Route
        path="/adm/*"
        element={
          <ProtectedRoute requireAdmin>
            <AdmLayout />
          </ProtectedRoute>
        }
      />

      {/* 사용자 영역 (/gen/*) — 공개(비로그인 게스트 포함). 메뉴·콘텐츠 노출 범위는 권한그룹(GUEST/MEMBER)이 결정 */}
      <Route path="/gen/*" element={<GenLayout />} />

      {/* 루트는 기본 진입점(/gen)으로, 그 외 알 수 없는 경로는 404 */}
      <Route path="/" element={<Navigate to="/gen" replace />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
