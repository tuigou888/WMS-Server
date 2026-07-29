import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: { rollupOptions: { output: { manualChunks: { react: ['react', 'react-dom'], antd: ['antd', '@ant-design/icons'], charts: ['recharts'] } } } },
  server: { port: 5173, proxy: { '/api/v1': { target: 'http://localhost:8088', changeOrigin: true } } }
})
