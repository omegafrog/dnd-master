import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: 'http://127.0.0.1:15174',
    browserName: 'chromium',
    headless: true,
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 15174 --strictPort',
    url: 'http://127.0.0.1:15174/e2e/fixtures/index.html',
    reuseExistingServer: false,
    timeout: 30_000,
    env: {
      VITE_BACKEND_E2E_URL: process.env.BACKEND_E2E_URL ?? '',
      VITE_BACKEND_E2E_ADVENTURE_ID: process.env.BACKEND_E2E_ADVENTURE_ID ?? '',
      VITE_BACKEND_E2E_PLAYER_ID: process.env.BACKEND_E2E_PLAYER_ID ?? '',
    },
  },
})
