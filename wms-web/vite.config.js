import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: { rollupOptions: { output: { manualChunks: { vue: ['vue', 'vue-router', 'pinia'], antd: ['ant-design-vue', '@ant-design/icons-vue'], charts: ['echarts'] } } } },
  server: { port: 5173, proxy: { '/api/v1': { target: 'http://localhost:8088', changeOrigin: true } } }
})
