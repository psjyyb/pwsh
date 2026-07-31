import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { message } from 'antd'
import { tokenStore, isAdmin } from './token'

/** 접근 거부 시 안내 후 사용자 영역(/gen)으로 이동 */
function AccessDenied() {
  useEffect(() => {
    message.error('접근 권한이 없는 페이지입니다.')
  }, [])
  return <Navigate to="/gen" replace />
}

/**
 * 라우트 가드.
 * - 토큰 없으면 로그인으로.
 * - requireAdmin=true인데 관리자(MEM02)가 아니면 안내 후 /gen으로.
 */
export default function ProtectedRoute({
  children,
  requireAdmin = false,
}: {
  children: ReactNode
  requireAdmin?: boolean
}) {
  if (!tokenStore.get()) return <Navigate to="/login" replace />
  if (requireAdmin && !isAdmin()) return <AccessDenied />
  return <>{children}</>
}
