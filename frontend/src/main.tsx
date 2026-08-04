import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider, Empty } from 'antd'
import koKR from 'antd/locale/ko_KR'
import App from './App.tsx'
import ErrorBoundary from './common/ErrorBoundary'
import { fontStack } from './gen/theme'
import './index.css'

// 빈 Table만 한국어 Empty로 통일(관리자 목록). Select 등 나머지는 AntD 기본(compact)로 두어 과하지 않게.
// renderEmpty는 컴포넌트명을 받으므로 Table일 때만 커스텀, 그 외는 undefined 반환 → 기본 사용.
const renderEmpty = (componentName?: string) =>
  componentName === 'Table' ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="데이터가 없습니다." /> : undefined

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider locale={koKR} renderEmpty={renderEmpty} theme={{ token: { fontFamily: fontStack, lineHeight: 1.6 } }}>
      <ErrorBoundary>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </ErrorBoundary>
    </ConfigProvider>
  </StrictMode>,
)
