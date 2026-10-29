import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// El proxy reenvía /api al backend para que el navegador no necesite CORS en
// desarrollo. También elimina el header Origin: el backend valida CORS por ese
// header y, sin él, la app funciona igual desde localhost o desde la IP de la
// red local (por ejemplo, al probar en un teléfono).
const proxyApi = {
  target: process.env.VITE_BACKEND || 'http://localhost:8081',
  changeOrigin: true,
  configure: (proxy) => {
    proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
  },
}

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': proxyApi,
    },
  },
})