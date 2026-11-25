import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발 서버: /api 요청은 백엔드(8080)로 프록시 → CORS 회피, 배포 시 리버스 프록시와 동일 구조
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 검색엔진은 사이트 루트에서 찾는다(1.2.43) — 백엔드의 공개 출력으로 넘긴다. 운영 앞단(nginx 등)도 같은 연결이 필요하다
      '/sitemap.xml': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: () => '/api/pub/seo/sitemap.xml',
      },
      '/robots.txt': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: () => '/api/pub/seo/robots.txt',
      },
    },
  },
})
