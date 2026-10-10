import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '0.0.0.0',
    proxy: {
      '/api': { target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080' },
      '/ws': { target: (process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080').replace('http', 'ws'), ws: true },
    },
  },
  test: { environment: 'jsdom', setupFiles: './src/test-setup.ts', include: ['src/**/*.test.{ts,tsx}'], pool: 'threads', maxWorkers: 1, fileParallelism: false },
})
