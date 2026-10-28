import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// El proxy reenvía /api al backend, así el navegador no necesita CORS en desarrollo.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND || 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
