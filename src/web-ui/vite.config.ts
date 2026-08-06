import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: process.env.BACKEND_E2E_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/internal': {
        target: process.env.BACKEND_E2E_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    restoreMocks: true,
    include: ['src/**/*.test.tsx'],
    exclude: ['e2e/**'],
  },
})
