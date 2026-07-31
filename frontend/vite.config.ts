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
    },
  },
})
