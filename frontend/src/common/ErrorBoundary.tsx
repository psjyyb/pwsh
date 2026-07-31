import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { Button, Result } from 'antd'

interface Props {
  children: ReactNode
}
interface State {
  hasError: boolean
  message?: string
}

/**
 * 전역 에러 경계 — 하위 트리의 렌더 예외를 잡아 화이트스크린 대신 복구 UI를 표시한다.
 * (React 에러 경계는 클래스 컴포넌트로만 구현 가능.) 앱 최상단(main.tsx)에서 App을 감싼다.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, message: error.message }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary]', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <Result
          status="error"
          title="오류가 발생했습니다"
          subTitle={this.state.message || '화면을 표시하는 중 문제가 발생했습니다.'}
          extra={
            <Button type="primary" onClick={() => window.location.reload()}>
              새로고침
            </Button>
          }
        />
      )
    }
    return this.props.children
  }
}
